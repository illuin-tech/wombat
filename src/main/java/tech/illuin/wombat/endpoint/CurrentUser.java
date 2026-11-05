package tech.illuin.wombat.endpoint;

import io.quarkus.security.identity.SecurityIdentity;
import tech.illuin.wombat.security.AuthProperties;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.IdentityClaims;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.Role;

import java.util.List;
import java.util.Optional;

public record CurrentUser(
    boolean authenticated,
    String name,
    List<String> roles,
    List<String> permissions
)
{
    static final CurrentUser ANONYMOUS = new CurrentUser(false, null, List.of(), List.of());

    public static CurrentUser from(SecurityIdentity identity, AuthProperties properties)
    {
        if (identity == null || (identity.isAnonymous() && properties.enabled()))
            return ANONYMOUS;

        boolean authenticated = !identity.isAnonymous();
        return new CurrentUser(
            authenticated,
            authenticated ? name(identity, properties.displayNameClaims()) : null,
            Caller.roles(identity).stream().sorted().map(Role::value).toList(),
            Caller.permissions(identity).stream().map(Permission::value).sorted().toList()
        );
    }

    public boolean canManageApiKeys()
    {
        return this.permissions.contains(Permission.UI_APIKEYS_MANAGE.value());
    }

    public boolean canViewUsers()
    {
        return this.permissions.contains(Permission.UI_USERS_READ.value());
    }

    private static String name(SecurityIdentity identity, List<String> displayNameClaims)
    {
        return displayNameClaims.stream()
            .map(claim -> IdentityClaims.string(identity, claim))
            .flatMap(Optional::stream)
            .findFirst()
            .orElseGet(() -> identity.getPrincipal() == null ? null : identity.getPrincipal().getName());
    }
}
