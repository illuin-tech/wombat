package tech.illuin.wombat.ui;

import io.quarkus.security.identity.SecurityIdentity;
import tech.illuin.wombat.security.IdentityClaims;

import java.util.List;
import java.util.Optional;

public record CurrentUser(
    boolean authenticated,
    String name
)
{
    static final CurrentUser ANONYMOUS = new CurrentUser(false, null);

    static CurrentUser from(SecurityIdentity identity, List<String> displayNameClaims)
    {
        if (identity == null || identity.isAnonymous())
            return ANONYMOUS;

        return new CurrentUser(true, name(identity, displayNameClaims));
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
