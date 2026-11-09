package tech.illuin.wombat.engine.secret;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(SecretControllerDisabledAuthenticationTest.Profile.class)
class SecretControllerDisabledAuthenticationTest
{
    @Inject SecretStore store;

    @Test
    void anonymousCallersCanManageSecretsWhenAuthenticationIsDisabled() throws Exception
    {
        String key = "anonymous-secret-" + System.nanoTime();
        String value = "anonymous-private-value-" + System.nanoTime();
        try {
            String putResponse = given().contentType(ContentType.JSON).body(Map.of("key", key, "value", value))
                .when().put("/api/secrets").then().statusCode(200).extract().asString();
            assertFalse(putResponse.contains(value));

            String listed = given().when().get("/api/secrets").then().statusCode(200).extract().asString();
            assertTrue(listed.contains(key));
            assertFalse(listed.contains(value));

            String rewrap = given().when().post("/api/secrets/database/rewrap").then().statusCode(200).extract().asString();
            assertFalse(rewrap.contains(value));

            given().queryParam("key", key).when().delete("/api/secrets").then().statusCode(200);

            JsonNode openApi = new ObjectMapper().readTree(given().accept(ContentType.JSON).when().get("/api/openapi")
                .then().statusCode(200).extract().asString());
            assertTrue(openApi.path("paths").has("/api/secrets"));
            assertTrue(openApi.path("paths").has("/api/secrets/database/rewrap"));
        }
        finally {
            QuarkusTransaction.requiringNew().run(() -> this.store.deleteSecret(key));
        }
    }

    public static class Profile implements QuarkusTestProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            String kek = Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
            return Map.of(
                "wombat.secret.write-mode", "DATABASE",
                "wombat.secret.database.active-kek-id", "test",
                "wombat.secret.database.kek.test", kek
            );
        }
    }
}