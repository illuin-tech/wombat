package tech.illuin.wombat.engine.secret;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.security.PermissionAugmentor;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
@TestProfile(SecretControllerBodyLimitTest.Profile.class)
class SecretControllerBodyLimitTest
{
    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "email", value = "boss@example.com"))
    void enforcesTheConfiguredHttpBodyLimit()
    {
        String value = "private-value-".repeat(100);
        String response = given().contentType(ContentType.JSON).body(Map.of("key", "large", "value", value))
            .when().put("/api/secrets").then().statusCode(413).extract().asString();

        assertFalse(response.contains(value));
    }

    public static class Profile extends AuthenticationEnabledTest.AuthenticationEnabledProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            Map<String, String> values = new HashMap<>(super.getConfigOverrides());
            values.put("quarkus.http.limits.max-body-size", "256");
            values.put("wombat.secret.write-mode", "DATABASE");
            values.put("wombat.secret.database.active-kek-id", "test");
            values.put("wombat.secret.database.kek.test", Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
            ));
            return values;
        }
    }
}