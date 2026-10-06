package tech.illuin.wombat.ui;

import io.quarkus.oidc.UserInfo;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.json.JsonString;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Represents the currently signed-in user for Qute templates (exposed as {@code currentUser} in {@link UIConfig}).
 */
public record CurrentUser(
    boolean authenticated,
    String name
)
{
    /** {@code io.quarkus.oidc.runtime.OidcUtils#USER_INFO_ATTRIBUTE} */
    private static final String USER_INFO_ATTRIBUTE = "userinfo";
    private static final String EMAIL_CLAIM = "email";

    static final CurrentUser ANONYMOUS = new CurrentUser(false, null);

    static CurrentUser from(SecurityIdentity identity)
    {
        if (identity == null || identity.isAnonymous())
            return ANONYMOUS;

        return new CurrentUser(true, name(identity));
    }

    private static String name(SecurityIdentity identity)
    {
        if (identity.getPrincipal() instanceof JsonWebToken token)
        {
            String claim = asString(token.getClaim(EMAIL_CLAIM));
            if (claim != null && !claim.isBlank())
                return claim;
        }

        UserInfo userInfo = identity.getAttribute(USER_INFO_ATTRIBUTE);
        if (userInfo != null && userInfo.getEmail() != null && !userInfo.getEmail().isBlank())
            return userInfo.getEmail();

        return identity.getPrincipal() == null ? null : identity.getPrincipal().getName();
    }

    private static String asString(Object value)
    {
        return switch (value)
        {
            case null -> null;
            case String string -> string;
            case JsonString json -> json.getString();
            default -> value.toString();
        };
    }
}
