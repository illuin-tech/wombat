package tech.illuin.wombat.endpoint.secret;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.secret.SecretStore;
import tech.illuin.wombat.engine.secret.audit.LogAuditService;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(SecretControllerDisabledAuthenticationTest.Profile.class)
class SecretControllerDisabledAuthenticationTest
{
    @Inject SecretStore store;

    @Test
    void anonymousCallersCanManageSecretsAndAreAuditedWhenAuthenticationIsDisabled() throws Exception
    {
        String key = "anonymous-secret-" + System.nanoTime();
        String value = "anonymous-private-value-" + System.nanoTime();
        List<String> audits = new CopyOnWriteArrayList<>();
        Logger root = Logger.getLogger("");
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record)
            {
                if (LogAuditService.class.getName().equals(record.getLoggerName()))
                {
                    String message = record instanceof ExtLogRecord ext ? ext.getFormattedMessage() : record.getMessage();
                    if (message != null)
                        audits.add(message);
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        root.addHandler(handler);
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
            root.removeHandler(handler);
            QuarkusTransaction.requiringNew().run(() -> this.store.deleteSecret(key));
        }

        assertTrue(audits.stream().anyMatch(message -> message.contains("actor=anonymous")
            && message.contains("operation=put") && message.contains("outcome=success")));
        assertTrue(audits.stream().anyMatch(message -> message.contains("actor=anonymous")
            && message.contains("operation=list") && message.contains("outcome=success")));
        assertTrue(audits.stream().anyMatch(message -> message.contains("actor=anonymous")
            && message.contains("operation=rewrap") && message.contains("outcome=success")));
        assertTrue(audits.stream().anyMatch(message -> message.contains("actor=anonymous")
            && message.contains("operation=delete") && message.contains("outcome=success")));
        assertTrue(audits.stream().noneMatch(message -> message.contains(value) || message.contains(key)));
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