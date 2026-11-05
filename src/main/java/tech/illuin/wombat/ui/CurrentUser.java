package tech.illuin.wombat.ui;

import io.quarkus.security.identity.SecurityIdentity;
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
    boolean canManageApiKeys,
    boolean canViewUsers
)
{
    static final CurrentUser ANONYMOUS = new CurrentUser(false, null, List.of(), false, false);

    static CurrentUser from(SecurityIdentity identity, List<String> displayNameClaims)
    {
        if (identity == null || identity.isAnonymous())
            return ANONYMOUS;

        return new CurrentUser(
            true,
            name(identity, displayNameClaims),
            roles(identity),
            Caller.permissions(identity).contains(Permission.UI_APIKEYS_MANAGE),
            Caller.permissions(identity).contains(Permission.UI_USERS_READ)
        );
    }

    private static List<String> roles(SecurityIdentity identity)
    {
        return Caller.roles(identity).stream()
            .sorted()
            .map(Role::value)
            .toList();
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
