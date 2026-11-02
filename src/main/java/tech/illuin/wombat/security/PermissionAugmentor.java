package tech.illuin.wombat.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.jwt.JsonWebToken;
import tech.illuin.wombat.security.apikey.ApiKeyService;
import tech.illuin.wombat.security.apikey.ApiKeyTenantResolver;
import tech.illuin.wombat.security.user.RoleGrantService;
import tech.illuin.wombat.security.user.UserActivity;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class PermissionAugmentor implements SecurityIdentityAugmentor
{
    // io.quarkus.oidc.runtime.OidcUtils#TENANT_ID_ATTRIBUTE
    private static final String TENANT_ID_ATTRIBUTE = "tenant-id";

    private final AuthProperties properties;
    private final RoleMapping roleMapping;
    private final ApiKeyService apiKeyService;
    private final UserActivity userActivity;
    private final RoleGrantService roleGrants;

    public PermissionAugmentor(AuthProperties properties, RoleMapping roleMapping, ApiKeyService apiKeyService, UserActivity userActivity, RoleGrantService roleGrants)
    {
        this.roleGrants = roleGrants;
        this.properties = properties;
        this.roleMapping = roleMapping;
        this.apiKeyService = apiKeyService;
        this.userActivity = userActivity;
    }

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context)
    {
        if (identity.isAnonymous())
        {
            if (this.properties.enabled())
                return Uni.createFrom().item(identity);
            return Uni.createFrom().item(withPermissions(identity, EnumSet.allOf(Role.class), EnumSet.allOf(Permission.class)));
        }

        if (ApiKeyTenantResolver.TENANT_ID.equals(identity.getAttribute(TENANT_ID_ATTRIBUTE)))
            return context.runBlocking(() -> this.fromApiKey(identity));

        Set<Role> mapped = this.roleMapping.map(identity);
        Caller caller = Caller.from(identity, this.properties, mapped);
        boolean recordActivity = this.userActivity.isDue(caller);
        if (!recordActivity && !this.roleGrants.isStale())
            return Uni.createFrom().item(this.withGrantedRoles(identity, caller));

        return context.runBlocking(() -> {
            this.roleGrants.reloadIfStale();
            if (recordActivity)
                this.userActivity.record(caller);
            return this.withGrantedRoles(identity, caller);
        });
    }

    private SecurityIdentity fromApiKey(SecurityIdentity identity)
    {
        if (!(identity.getPrincipal() instanceof JsonWebToken token) || token.getTokenID() == null)
            throw new AuthenticationFailedException("API key without an id");

        ApiKeyService.Grant grant = this.apiKeyService.authenticate(token.getTokenID())
            .orElseThrow(() -> new AuthenticationFailedException("API key " + token.getTokenID() + " is revoked, expired or its owner lost access"));

        return QuarkusSecurityIdentity.builder()
            .setPrincipal(new QuarkusPrincipal(grant.principal()))
            .addCredentials(identity.getCredentials())
            .addAttribute(TENANT_ID_ATTRIBUTE, ApiKeyTenantResolver.TENANT_ID)
            .addAttribute(Caller.API_KEY_ATTRIBUTE, grant.keyId())
            .addAttribute(Caller.ROLES_ATTRIBUTE, EnumSet.noneOf(Role.class))
            .addAttribute(Caller.PERMISSIONS_ATTRIBUTE, grant.permissions())
            .addPermissionsAsString(values(grant.permissions()))
            .build();
    }

    /* Mapped roles plus those granted in Wombat: users are recorded with the mapped ones only, so the users page can tell them apart. */
    private SecurityIdentity withGrantedRoles(SecurityIdentity identity, Caller caller)
    {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        roles.addAll(caller.roles());
        roles.addAll(this.roleGrants.of(caller.issuer(), caller.subject()));
        return withPermissions(identity, roles, Role.permissionsOf(roles));
    }

    private static SecurityIdentity withPermissions(SecurityIdentity identity, Set<Role> roles, Set<Permission> permissions)
    {
        return QuarkusSecurityIdentity.builder(identity)
            .addAttribute(Caller.ROLES_ATTRIBUTE, roles)
            .addAttribute(Caller.PERMISSIONS_ATTRIBUTE, permissions)
            .addPermissionsAsString(values(permissions))
            .build();
    }

    private static Set<String> values(Set<Permission> permissions)
    {
        return permissions.stream().map(Permission::value).collect(Collectors.toSet());
    }
}
