package tech.illuin.wombat.security.apikey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyTokensTest
{
    @Test
    void generatedKeys_arePrefixedRandomAndUrlSafe()
    {
        String key = ApiKeyTokens.generate();

        assertTrue(key.matches("wmb_[A-Za-z0-9_-]{43}"), key);
        assertNotEquals(key, ApiKeyTokens.generate());
    }

    @Test
    void hash_isAStableSha256()
    {
        String key = ApiKeyTokens.generate();

        assertEquals(ApiKeyTokens.hash(key), ApiKeyTokens.hash(key));
        assertTrue(ApiKeyTokens.hash(key).matches("[0-9a-f]{64}"));
        assertNotEquals(ApiKeyTokens.hash(key), ApiKeyTokens.hash(ApiKeyTokens.generate()));
    }

    @Test
    void apiKeyHeader_isClaimedByWombat()
    {
        assertEquals("wmb_abc", ApiKeyAuthenticationMechanism.headerApiKey("wmb_abc"));
        assertEquals("wmb_abc", ApiKeyAuthenticationMechanism.headerApiKey("  wmb_abc "));
    }

    @Test
    void missingOrBlankHeader_isLeftToTheIdentityProvider()
    {
        assertNull(ApiKeyAuthenticationMechanism.headerApiKey(null));
        assertNull(ApiKeyAuthenticationMechanism.headerApiKey(" "));
        assertFalse(ApiKeyTokens.isApiKey(null));
        assertFalse(ApiKeyTokens.isApiKey("eyJhbGciOiJSUzI1NiJ9.e30.sig"));
    }
}
