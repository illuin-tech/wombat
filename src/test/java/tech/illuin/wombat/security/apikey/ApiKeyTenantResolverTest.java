package tech.illuin.wombat.security.apikey;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyTenantResolverTest
{
    @Test
    void wombatIssuedBearerTokens_goToTheApiKeyTenant()
    {
        assertTrue(ApiKeyTenantResolver.isIssuedByWombat("Bearer " + jwt("{\"iss\":\"wombat\"}")));
        assertTrue(ApiKeyTenantResolver.isIssuedByWombat("bearer " + jwt("{\"iss\":\"wombat\"}")));
    }

    @Test
    void otherTokens_goToTheIdentityProvider()
    {
        assertFalse(ApiKeyTenantResolver.isIssuedByWombat(null));
        assertFalse(ApiKeyTenantResolver.isIssuedByWombat("Basic abc"));
        assertFalse(ApiKeyTenantResolver.isIssuedByWombat("Bearer " + jwt("{\"iss\":\"https://idp\"}")));
        assertFalse(ApiKeyTenantResolver.isIssuedByWombat("Bearer opaque"));
        assertFalse(ApiKeyTenantResolver.isIssuedByWombat("Bearer a.!!!.c"));
    }

    private static String jwt(String claims)
    {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{}".getBytes(StandardCharsets.UTF_8)) + "." + encoder.encodeToString(claims.getBytes(StandardCharsets.UTF_8)) + ".sig";
    }
}
