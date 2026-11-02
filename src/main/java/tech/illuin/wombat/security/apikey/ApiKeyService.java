package tech.illuin.wombat.security.apikey;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import tech.illuin.wombat.security.AuthProperties;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.user.UserEntity;
import tech.illuin.wombat.security.user.UserRepository;
import tech.illuin.wombat.security.user.UserService;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static tech.illuin.wombat.engine.context.persistence.EnvironmentEntity.now;

/** API keys are managed by admins and belong to no one: a key is valid until it expires or is revoked, whoever created it. */
@ApplicationScoped
public class ApiKeyService
{
    private static final Duration LAST_USED_RESOLUTION = Duration.ofMinutes(1);
    private static final String PRINCIPAL_PREFIX = "api-key:";

    private final ApiKeyRepository keys;
    private final UserRepository users;
    private final UserService userService;
    private final ApiKeySigningKey signingKey;
    private final AuthProperties properties;

    public ApiKeyService(
        ApiKeyRepository keys,
        UserRepository users,
        UserService userService,
        ApiKeySigningKey signingKey,
        AuthProperties properties
    ) {
        this.keys = keys;
        this.users = users;
        this.userService = userService;
        this.signingKey = signingKey;
        this.properties = properties;
    }

    @Transactional
    public Optional<Grant> authenticate(String keyId)
    {
        Instant now = now();
        ApiKeyEntity key = this.keys.findById(keyId);
        if (key == null || !key.isActive(now))
            return Optional.empty();

        if (key.lastUsedAt == null || key.lastUsedAt.plus(LAST_USED_RESOLUTION).isBefore(now))
            key.lastUsedAt = now;
        return Optional.of(new Grant(key.id, PRINCIPAL_PREFIX + key.name, grantableOnly(key.permissions)));
    }

    @Transactional
    public Issued create(Caller caller, String name, Collection<String> permissions, Instant expiresAt)
    {
        Set<Permission> granted = grantable(permissions, caller.permissions());
        UserEntity creator = this.userService.ensure(caller);

        ApiKeyEntity key = this.newKey(creator, name, granted, expiresAt);
        key.persist();
        return new Issued(key, this.signingKey.sign(key, PRINCIPAL_PREFIX + key.id));
    }

    /** Every key, newest first, with the name of the admin who created it. */
    @Transactional
    public List<Listed> list()
    {
        List<ApiKeyEntity> all = this.keys.findAllNewestFirst();
        List<String> creatorIds = all.stream().map(key -> key.createdBy).filter(Objects::nonNull).distinct().toList();
        Map<String, UserEntity> creators = creatorIds.isEmpty()
            ? Map.of()
            : this.users.list("id in ?1", creatorIds).stream().collect(Collectors.toMap(user -> user.id, Function.identity()));

        return all.stream()
            .map(key -> new Listed(key, displayName(creators.get(key.createdBy), key.createdBy)))
            .toList();
    }

    @Transactional
    public void revoke(String keyId)
    {
        ApiKeyEntity key = this.keys.findByIdOptional(keyId).orElseThrow(NotFoundException::new);
        if (key.revokedAt == null)
            key.revokedAt = now();
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

    private ApiKeyEntity newKey(UserEntity creator, String name, Set<Permission> permissions, Instant expiresAt)
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
        key.createdBy = creator.id;
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

    public record Listed(ApiKeyEntity key, String createdBy) {}
}
