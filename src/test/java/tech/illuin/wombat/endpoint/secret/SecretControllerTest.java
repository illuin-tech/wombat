package tech.illuin.wombat.endpoint.secret;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.engine.secret.SecretStore;
import tech.illuin.wombat.engine.secret.database.crypto.KeyEncryptionKeyProvider;
import tech.illuin.wombat.engine.secret.database.crypto.SecretCryptography;
import tech.illuin.wombat.engine.secret.database.persistence.SecretEntity;
import tech.illuin.wombat.engine.secret.database.persistence.SecretRepository;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.PermissionAugmentor;
import tech.illuin.wombat.security.Role;
import tech.illuin.wombat.security.apikey.ApiKeyAuthenticationMechanism;
import tech.illuin.wombat.security.apikey.ApiKeyService;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(SecretControllerTest.Profile.class)
class SecretControllerTest
{
    @Inject SecretStore store;
    @Inject SecretResolver resolver;
    @Inject ApiKeyService apiKeys;
    @Inject SecretRepository repository;

    private static final String TEST_KEK = Base64.getEncoder().encodeToString(
        "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
    );
    private static final String OLD_KEK = Base64.getEncoder().encodeToString(
        "old-0123456789012345678901234567".getBytes(StandardCharsets.UTF_8)
    );

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "sub", value = "secret-admin"),
        @Claim(key = "email", value = "boss@example.com")
    })
    void adminManagesSecretsWithoutReturningOrLoggingValues()
    {
        String key = "http-secret-" + UUID.randomUUID();
        String value = "private-value-" + UUID.randomUUID();
        String replacement = "replacement-value-" + UUID.randomUUID();
        String request = "{\"key\":\"" + key + "\",\"value\":\"" + value + "\"}";
        List<String> logs = new CopyOnWriteArrayList<>();
        Logger root = Logger.getLogger("");
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record)
            {
                String message = record instanceof ExtLogRecord ext ? ext.getFormattedMessage() : record.getMessage();
                if (message != null)
                    logs.add(message);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        root.addHandler(handler);
        try {
            String putResponse = given().contentType(ContentType.JSON).body(request)
                .when().put("/api/secrets").then().statusCode(200).extract().asString();
            assertFalse(putResponse.contains(value));
            assertEquals(value, this.resolver.require(key));

            io.restassured.response.Response listed = given().when().get("/api/secrets").then().statusCode(200)
                .contentType(ContentType.JSON).extract().response();
            assertFalse(listed.asString().contains(value));
            assertFalse(listed.asString().contains(TEST_KEK));
            Map<String, Object> metadata = listed.path("payload.find { it.key == '" + key + "' }");
            assertNotNull(metadata);
            assertEquals(Set.of("key", "createdAt", "updatedAt"), metadata.keySet());
            assertNotNull(metadata.get("createdAt"));
            assertNotNull(metadata.get("updatedAt"));

            String invalidResponse = given().contentType(ContentType.JSON).body(request.substring(0, request.length() - 1))
                .when().put("/api/secrets").then().statusCode(400).extract().asString();
            assertFalse(invalidResponse.contains(value));
            given().contentType(ContentType.JSON).body("{\"key\":\" \",\"value\":\"" + value + "\"}")
                .when().put("/api/secrets").then().statusCode(400);
            given().contentType(ContentType.JSON).body("{\"key\":\"" + key + "\"}")
                .when().put("/api/secrets").then().statusCode(400);
            given().contentType(ContentType.JSON).body(Map.of("key", key, "value", " "))
                .when().put("/api/secrets").then().statusCode(400);
            given().when().delete("/api/secrets").then().statusCode(400);

            String updateResponse = given().contentType(ContentType.JSON).body(Map.of("key", key, "value", replacement))
                .when().put("/api/secrets").then().statusCode(200).extract().asString();
            assertFalse(updateResponse.contains(replacement));
            assertEquals(replacement, this.resolver.require(key));
            given().when().post("/api/secrets/database/rewrap").then().statusCode(200).contentType(ContentType.JSON)
                .body("payload.remaining", org.hamcrest.Matchers.is(0));
            given().queryParam("key", key).when().delete("/api/secrets").then().statusCode(200);
            given().queryParam("key", key).when().delete("/api/secrets").then().statusCode(404);
            assertTrue(this.resolver.find(key).isEmpty());
        }
        finally {
            root.removeHandler(handler);
            this.store.deleteSecret(key);
        }

        assertTrue(logs.stream().anyMatch(message -> message.contains("actor=secret-admin") && message.contains("operation=put") && message.contains("outcome=success")));
        assertTrue(logs.stream().anyMatch(message -> message.contains("operation=put") && message.contains("outcome=invalid")));
        assertTrue(logs.stream().anyMatch(message -> message.contains("actor=secret-admin")
            && message.contains("operation=rewrap") && message.contains("outcome=success")));
        assertTrue(logs.stream().noneMatch(message -> message.contains(value) || message.contains(replacement) || message.contains(TEST_KEK) || message.contains(request)));
    }

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "email", value = "boss@example.com"))
    void rewrapsAnOldKeyThroughTheHttpRoute()
    {
        String key = "http-rewrap-" + UUID.randomUUID();
        String value = "private-rewrap-value-" + UUID.randomUUID();
        KeyEncryptionKeyProvider oldKey = new KeyEncryptionKeyProvider() {
            @Override
            public String activeKeyId()
            {
                return "old";
            }

            @Override
            public byte[] key(String id)
            {
                return Base64.getDecoder().decode(OLD_KEK);
            }
        };
        SecretCryptography.EncryptedSecret encrypted = new SecretCryptography(oldKey).encrypt(key, value);
        SecretEntity entity = new SecretEntity();
        entity.key = key;
        entity.kekId = encrypted.kekId();
        entity.ciphertext = encrypted.ciphertext();
        entity.payloadNonce = encrypted.payloadNonce();
        entity.wrappedDek = encrypted.wrappedDek();
        entity.wrappingNonce = encrypted.wrappingNonce();
        QuarkusTransaction.requiringNew().run(() -> this.repository.persist(entity));
        try {
            String response = given().when().post("/api/secrets/database/rewrap").then().statusCode(200)
                .body("payload.rewrapped", org.hamcrest.Matchers.is(1))
                .body("payload.remaining", org.hamcrest.Matchers.is(0)).extract().asString();
            assertFalse(response.contains(value));
            SecretEntity rewrapped = QuarkusTransaction.requiringNew().call(() -> this.repository.findById(key));
            assertEquals("test", rewrapped.kekId);
            assertArrayEquals(encrypted.ciphertext(), rewrapped.ciphertext);
            assertArrayEquals(encrypted.payloadNonce(), rewrapped.payloadNonce);
            assertEquals(value, this.resolver.require(key));
            given().when().post("/api/secrets/database/rewrap").then().statusCode(200)
                .body("payload.rewrapped", org.hamcrest.Matchers.is(0));
        }
        finally {
            this.store.deleteSecret(key);
        }
    }

    @Test
    @TestSecurity(user = "user", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "email", value = "user@example.com"))
    void ordinaryUserCannotManageSecrets()
    {
        assertDenied(403, null);
    }

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "email", value = "boss@example.com"))
    void openApiDescribesTheWriteOnlySecretValue() throws Exception
    {
        String document = given().accept(ContentType.JSON).when().get("/api/openapi").then().statusCode(200).extract().asString();
        JsonNode openApi = new ObjectMapper().readTree(document);
        JsonNode secrets = openApi.path("paths").path("/api/secrets");
        assertTrue(secrets.path("get").isObject());
        assertTrue(secrets.path("put").isObject());
        assertTrue(secrets.path("delete").isObject());
        assertTrue(openApi.path("paths").path("/api/secrets/database/rewrap").path("post").isObject());
        assertTrue(openApi.path("components").path("schemas").path("SecretWriteRequest")
            .path("properties").path("value").path("writeOnly").asBoolean());
        assertTrue(openApi.path("components").path("schemas").path("SecretMetadata").path("properties").path("key").isObject());
        assertFalse(openApi.path("components").path("schemas").path("SecretMetadata").path("properties").has("value"));
        assertTrue(openApi.path("components").path("schemas").path("SecretRewrap").path("properties").path("remaining").isObject());
    }

    @Test
    @TestSecurity(user = "manager", roles = "wombat-key-managers", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "email", value = "manager@example.com"))
    void apiKeyManagerCannotManageSecrets()
    {
        assertDenied(403, null);
    }

    @Test
    void anonymousCallerCannotManageSecrets()
    {
        assertDenied(401, null);
    }

    @Test
    void apiKeyCannotManageSecrets()
    {
        Caller admin = new Caller("https://idp", UUID.randomUUID().toString(), "admin", null, EnumSet.of(Role.ADMIN), Role.ADMIN.permissions());
        String token = this.apiKeys.create(
            admin,
            "secret-test",
            List.of(Permission.Names.API_ENVIRONMENT_READ),
            Instant.now().plus(Duration.ofDays(1))
        ).token();
        assertDenied(403, token);
    }

    private static void assertDenied(int status, String token)
    {
        request(token).when().get("/api/secrets").then().statusCode(status);
        request(token).contentType(ContentType.JSON).body("{\"key\":\"key\",\"value\":\"value\"}")
            .when().put("/api/secrets").then().statusCode(status);
        request(token).queryParam("key", "key").when().delete("/api/secrets").then().statusCode(status);
        request(token).when().post("/api/secrets/database/rewrap").then().statusCode(status);
    }

    private static RequestSpecification request(String token)
    {
        RequestSpecification request = given();
        if (token != null)
            request.header(ApiKeyAuthenticationMechanism.HEADER, token);
        return request;
    }

    public static class Profile extends AuthenticationEnabledTest.AuthenticationEnabledProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            Map<String, String> values = new HashMap<>(super.getConfigOverrides());
            values.put("wombat.secret.write-mode", "DATABASE");
            values.put("wombat.secret.database.active-kek-id", "test");
            values.put("wombat.secret.database.kek.test", TEST_KEK);
            values.put("wombat.secret.database.kek.old", OLD_KEK);
            return values;
        }
    }
}