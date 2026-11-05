package tech.illuin.wombat.security.apikey;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import tech.illuin.wombat.security.AuthProperties;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.Role;
import tech.illuin.wombat.security.user.RoleGrantService;
import tech.illuin.wombat.security.user.UserEntity;
import tech.illuin.wombat.security.user.UserRepository;
import tech.illuin.wombat.security.user.UserService;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static tech.illuin.wombat.engine.context.persistence.EnvironmentEntity.now;

/** Every user manages their own API keys, admins see and revoke everyone's; a key never grants more than its owner holds. */
@ApplicationScoped
public class ApiKeyService
{
    private static final Duration LAST_USED_RESOLUTION = Duration.ofMinutes(1);
    private static final String PRINCIPAL_PREFIX = "api-key:";

    private final ApiKeyRepository keys;
    private final UserRepository users;
    private final UserService userService;
    private final RoleGrantService roleGrants;
    private final AuthProperties properties;

    public ApiKeyService(
        ApiKeyRepository keys,
        UserRepository users,
        UserService userService,
        RoleGrantService roleGrants,
        AuthProperties properties
    ) {
        this.keys = keys;
        this.users = users;
        this.userService = userService;
        this.roleGrants = roleGrants;
        this.properties = properties;
    }

    @Transactional
    public Optional<Grant> authenticate(String token)
    {
        if (!ApiKeyTokens.isApiKey(token))
            return Optional.empty();

        Instant now = now();
        ApiKeyEntity key = this.keys.findByHash(ApiKeyTokens.hash(token)).orElse(null);
        if (key == null || !key.isActive(now))
            return Optional.empty();

        Set<Permission> effective = grantableOnly(key.permissions);
        effective.retainAll(this.ownerPermissions(key.ownerId));
        if (effective.isEmpty())
            return Optional.empty();

        if (key.lastUsedAt == null || key.lastUsedAt.plus(LAST_USED_RESOLUTION).isBefore(now))
            key.lastUsedAt = now;
        return Optional.of(new Grant(key.id, PRINCIPAL_PREFIX + key.name, effective));
    }

    @Transactional
    public Issued create(Caller caller, String name, Collection<String> permissions, Instant expiresAt)
    {
        Set<Permission> granted = grantable(permissions, caller.permissions());
        UserEntity owner = this.userService.ensure(caller);

        String token = ApiKeyTokens.generate();
        ApiKeyEntity key = this.newKey(owner, name, granted, expiresAt);
        key.keyHash = ApiKeyTokens.hash(token);
        key.persist();
        return new Issued(key, token);
    }

    /** The caller's keys, or every user's keys when {@code all} is set (admins only), newest first with their owner's name. */
    @Transactional
    public List<Listed> list(Caller caller, boolean all)
    {
        List<ApiKeyEntity> listed;
        if (all)
        {
            if (!caller.permissions().contains(Permission.API_APIKEY_ADMIN))
                throw new ForbiddenException("Only admins can list every API key");
            listed = this.keys.findAllNewestFirst();
        }
        else
            listed = this.users.findByIdentity(caller.issuer(), caller.subject())
                .map(user -> this.keys.findByOwnerNewestFirst(user.id))
                .orElse(List.of());

        List<String> ownerIds = listed.stream().map(key -> key.ownerId).distinct().toList();
        Map<String, UserEntity> owners = ownerIds.isEmpty()
            ? Map.of()
            : this.users.list("id in ?1", ownerIds).stream().collect(Collectors.toMap(user -> user.id, Function.identity()));

        return listed.stream()
            .map(key -> new Listed(key, displayName(owners.get(key.ownerId), key.ownerId)))
            .toList();
    }

    /** Owners revoke their own keys, admins any key; someone else's key answers 404 so its existence is not disclosed. */
    @Transactional
    public void revoke(Caller caller, String keyId)
    {
        ApiKeyEntity key = this.keys.findByIdOptional(keyId).orElseThrow(NotFoundException::new);
        if (!caller.permissions().contains(Permission.API_APIKEY_ADMIN) && !this.isOwner(caller, key))
            throw new NotFoundException();
        if (key.revokedAt == null)
            key.revokedAt = now();
    }

    private boolean isOwner(Caller caller, ApiKeyEntity key)
    {
        return this.users.findByIdentity(caller.issuer(), caller.subject())
            .map(user -> user.id.equals(key.ownerId))
            .orElse(false);
    }

    private Set<Permission> ownerPermissions(String ownerId)
    {
        UserEntity owner = this.users.findById(ownerId);
        if (owner == null)
            return Set.of();
        Set<Role> roles = EnumSet.noneOf(Role.class);
        roles.addAll(owner.roles);
        roles.addAll(this.roleGrants.of(owner.issuer, owner.subject));
        return Role.permissionsOf(roles);
    }

    static Set<Permission> grantable(Collection<String> requested, Set<Permission> held)
    {
        if (requested == null || requested.isEmpty())
            throw new BadRequestException("At least one permission is required");

        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (String value : requested)
        {
            Permission permission = Permission.fromValue(value).orElseThrow(() -> new BadRequestException("Unknown permission " + value));
            if (!permission.grantableToApiKeys())
                throw new BadRequestException("Permission " + value + " cannot be granted to an API key");
            permissions.add(permission);
        }
        if (!held.containsAll(permissions))
            throw new ForbiddenException("You do not hold " + permissions.stream().filter(permission -> !held.contains(permission)).map(Permission::value).toList());
        return permissions;
    }

    private ApiKeyEntity newKey(UserEntity owner, String name, Set<Permission> permissions, Instant expiresAt)
    {
        if (name == null || name.isBlank())
            throw new BadRequestException("An API key needs a name");
        Instant now = now();
        if (expiresAt == null)
        {
            if (!this.properties.apiKeys().allowNeverExpiring())
                throw new BadRequestException("An API key needs an expiry, keys that never expire are disabled (auth.api-keys.allow-never-expiring)");
        }
        else if (!expiresAt.isAfter(now))
            throw new BadRequestException("An API key needs an expiry in the future");
        else if (expiresAt.isAfter(now.plus(this.properties.apiKeys().maxLifetime())))
            throw new BadRequestException("An API key cannot live longer than " + this.properties.apiKeys().maxLifetime());

        ApiKeyEntity key = new ApiKeyEntity();
        key.name = name.strip();
        key.permissions = permissions;
        key.ownerId = owner.id;
        key.expiresAt = expiresAt;
        return key;
    }

    /* A permission that stopped being grantable to keys is never honoured, even on keys created before. */
    private static Set<Permission> grantableOnly(Set<Permission> permissions)
    {
        Set<Permission> effective = EnumSet.noneOf(Permission.class);
        permissions.stream().filter(Permission::grantableToApiKeys).forEach(effective::add);
        return effective;
    }

    private static String displayName(UserEntity user, String fallback)
    {
        if (user == null)
            return fallback;
        return user.name != null ? user.name : user.subject;
    }

    public record Grant(String keyId, String principal, Set<Permission> permissions) {}

    public record Issued(ApiKeyEntity key, String token) {}

    public record Listed(ApiKeyEntity key, String owner) {}
}
