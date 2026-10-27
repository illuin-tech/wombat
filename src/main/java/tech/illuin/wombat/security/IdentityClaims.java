package tech.illuin.wombat.security;

import io.quarkus.oidc.UserInfo;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Optional;

public final class IdentityClaims
{
    /** {@code io.quarkus.oidc.runtime.OidcUtils#USER_INFO_ATTRIBUTE} */
    private static final String USER_INFO_ATTRIBUTE = "userinfo";

    private IdentityClaims() {}

    public static Optional<String> string(SecurityIdentity identity, String claim)
    {
        return raw(identity, claim)
            .map(IdentityClaims::asString)
            .filter(value -> !value.isBlank());
    }

    public static boolean isTrue(SecurityIdentity identity, String claim)
    {
        return raw(identity, claim)
            .map(value -> JsonValue.TRUE.equals(value) || Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(asString(value)))
            .orElse(false);
    }

    private static Optional<Object> raw(SecurityIdentity identity, String claim)
    {
        if (identity.getPrincipal() instanceof JsonWebToken token && token.getClaim(claim) != null)
            return Optional.of(token.getClaim(claim));

        UserInfo userInfo = identity.getAttribute(USER_INFO_ATTRIBUTE);
        if (userInfo != null && userInfo.contains(claim))
            return Optional.ofNullable(userInfo.get(claim));

        return Optional.empty();
    }

    private static String asString(Object value)
    {
        return switch (value)
        {
            case String string -> string;
            case JsonString json -> json.getString();
            default -> value.toString();
        };
    }
}
