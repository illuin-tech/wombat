package tech.illuin.wombat.engine.secret.vault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.All;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.quarkus.vault.client.VaultClient;
import io.quarkus.vault.client.VaultClientException;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretData;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretMetadataResultData;
import io.restassured.http.ContentType;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.secret.CompositeSecretResolver;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.core.secret.SecretWriter;
import tech.illuin.wombat.engine.secret.SecretConfig;
import tech.illuin.wombat.engine.secret.SecretProperties;
import tech.illuin.wombat.engine.secret.WritableBackend;
import tech.illuin.wombat.engine.secret.vault.client.VaultKvV2Client;
import tech.illuin.wombat.engine.secret.vault.client.VaultKvV2ClientAdapter;
import tech.illuin.wombat.security.PermissionAugmentor;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = VaultTestResource.class, restrictToAnnotatedClass = true)
@TestProfile(VaultSecretManagementIntegrationTest.Profile.class)
class VaultSecretManagementIntegrationTest
{
    @Inject SecretResolver resolver;
    @Inject SecretWriter writer;
    @Inject VaultClient client;
    @Inject VaultKvV2ClientAdapter vault;
    @Inject VaultSecretStore store;
    @Inject SecretProperties properties;
    @Inject @WritableBackend @All List<SecretConfig.SecretResolverCandidate> writableBackends;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "email", value = "boss@example.com"))
    void managesNestedSecretsAndExposesOnlyMetadata()
    {
        String key = "team/service-" + UUID.randomUUID();
        String value = "private-value-" + UUID.randomUUID();
        String replacement = "replacement-value-" + UUID.randomUUID();
        String path = VaultTestResource.PATH_PREFIX + "/" + key;

        try
        {
            String putResponse = given().contentType(ContentType.JSON).body(Map.of("key", key, "value", value))
                .when().put("/api/secrets").then().statusCode(200).extract().asString();
            assertFalse(putResponse.contains(value));
            assertEquals(value, this.resolver.require(key));

            assertEquals("manually-provisioned", this.resolver.require("manual/seed"));
            String listed = given().when().get("/api/secrets").then().statusCode(200)
                .contentType(ContentType.JSON).extract().asString();
            assertFalse(listed.contains(value));
            assertFalse(listed.contains(replacement));
            Map<String, Object> metadata = given().when().get("/api/secrets").then().statusCode(200)
                .extract().jsonPath().get("payload.find { it.key == '" + key + "' }");
            assertNotNull(metadata);
            assertEquals(Set.of("key", "createdAt", "updatedAt"), metadata.keySet());
            assertNotNull(metadata.get("createdAt"));
            assertNotNull(metadata.get("updatedAt"));

            this.writer.putSecret("priority", "vault-priority");
            assertEquals("vault-priority", this.resolver.require("priority"));
            assertEquals("directory-fallback", this.resolver.require("fallback-only"));
            assertTrue(this.writer.listSecrets().stream().noneMatch(entry -> entry.key().equals("fallback-only")));

            given().contentType(ContentType.JSON).body(Map.of("key", key, "value", replacement))
                .when().put("/api/secrets").then().statusCode(200);
            assertEquals(replacement, this.resolver.require(key));

            given().queryParam("key", key).when().delete("/api/secrets").then().statusCode(200);
            assertTrue(this.resolver.find(key).isEmpty());
            assertTrue(this.writer.listSecrets().stream().noneMatch(entry -> entry.key().equals(key)));
            assertEquals(value, this.vault.readSecret(path, 1).getData().get("value"));
            assertNotNull(this.vault.readSecretMetadata(path).orElseThrow().getVersions().get("2").getDeletionTime());
            given().queryParam("key", key).when().delete("/api/secrets").then().statusCode(404);
            assertTrue(this.writer.deleteSecret("priority"));
            assertEquals("directory-priority", this.resolver.require("priority"));

            given().contentType(ContentType.JSON).body(Map.of("key", "../outside", "value", value))
                .when().put("/api/secrets").then().statusCode(400);

            JsonNode openApi = objectMapper.readTree(given().accept(ContentType.JSON).when().get("/api/openapi")
                .then().statusCode(200).extract().asString());
            JsonNode secretPaths = openApi.path("paths").path("/api/secrets");
            assertTrue(secretPaths.path("get").isObject());
            assertTrue(secretPaths.path("put").isObject());
            assertTrue(secretPaths.path("delete").isObject());
            assertFalse(openApi.path("paths").has("/api/secrets/database/rewrap"));
        }
        catch (Exception exception)
        {
            throw new IllegalStateException("Vault secret integration request failed", exception);
        }
        finally
        {
            this.writer.deleteSecret(key);
            this.writer.deleteSecret("priority");
        }
    }

    @Test
    @TestSecurity(user = "user", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "email", value = "user@example.com"))
    void ordinaryUsersCannotManageVaultSecrets()
    {
        given().when().get("/api/secrets").then().statusCode(403);
        given().contentType(ContentType.JSON).body(Map.of("key", "forbidden", "value", "private"))
            .when().put("/api/secrets").then().statusCode(403);
        given().queryParam("key", "forbidden").when().delete("/api/secrets").then().statusCode(403);
    }

    @Test
    void selectedVaultStoreIsTheFirstResolverAndSharedWriter()
    {
        List<SecretConfig.SecretResolverCandidate> selected = this.writableBackends.stream()
            .filter(SecretConfig.SecretResolverCandidate::isElected)
            .toList();

        assertEquals(1, selected.size());
        assertSame(this.store, selected.getFirst().resolver());
        assertSame(this.store, this.writer);
        assertSame(this.store, assertInstanceOf(CompositeSecretResolver.class, this.resolver).resolvers().getFirst());
    }

    @Test
    void missingMountAndOutOfScopePathsFailInsteadOfFallingBack()
    {
        VaultSecretStore wrongMount = new VaultSecretStore(
            new VaultKvV2ClientAdapter(this.client, "not-mounted"),
            this.properties
        );

        assertThrows(VaultClientException.class, () -> wrongMount.find("missing"));
        assertThrows(VaultClientException.class, wrongMount::listSecrets);
        VaultClientException denied = assertThrows(
            VaultClientException.class,
            () -> this.vault.readSecretMetadata("outside-prefix/key")
        );
        assertEquals(403, denied.getStatus());
    }

    @Test
    void listsALeafAndDirectoryWithTheSameNameAndHandlesEmptyPrefixes()
    {
        String branch = "branch-" + UUID.randomUUID();
        try
        {
            this.writer.putSecret(branch, "leaf");
            this.writer.putSecret(branch + "/child", "nested");

            List<String> keys = this.writer.listSecrets().stream().map(metadata -> metadata.key()).toList();
            assertTrue(keys.contains(branch));
            assertTrue(keys.contains(branch + "/child"));

            SecretProperties emptyPrefix = properties(VaultTestResource.PATH_PREFIX + "/empty-" + UUID.randomUUID());
            assertTrue(new VaultSecretStore(this.vault, emptyPrefix).listSecrets().isEmpty());
        }
        finally
        {
            this.writer.deleteSecret(branch + "/child");
            this.writer.deleteSecret(branch);
        }
    }

    @Test
    void versionSpecificDeleteDoesNotDeleteAConcurrentNewerWrite()
    {
        String key = "race-" + UUID.randomUUID();
        String path = VaultTestResource.PATH_PREFIX + "/" + key;
        this.writer.putSecret(key, "original");

        VaultKvV2Client concurrentClient = new VaultKvV2Client()
        {
            @Override
            public List<String> listSecrets(String requestedPath)
            {
                return vault.listSecrets(requestedPath);
            }

            @Override
            public Optional<VaultSecretsKV2ReadSecretMetadataResultData> readSecretMetadata(String requestedPath)
            {
                Optional<VaultSecretsKV2ReadSecretMetadataResultData> observed = vault.readSecretMetadata(requestedPath);
                vault.writeSecret(requestedPath, "newer");
                return observed;
            }

            @Override
            public VaultSecretsKV2ReadSecretData readSecret(String requestedPath, int version)
            {
                return vault.readSecret(requestedPath, version);
            }

            @Override
            public void writeSecret(String requestedPath, String value)
            {
                vault.writeSecret(requestedPath, value);
            }

            @Override
            public void deleteSecretVersion(String requestedPath, int version)
            {
                vault.deleteSecretVersion(requestedPath, version);
            }
        };

        try
        {
            assertTrue(new VaultSecretStore(concurrentClient, this.properties).deleteSecret(key));
            assertEquals("newer", this.resolver.require(key));
            VaultSecretsKV2ReadSecretMetadataResultData metadata = this.vault.readSecretMetadata(path).orElseThrow();
            assertNotNull(metadata.getVersions().get("1").getDeletionTime());
            assertNull(metadata.getVersions().get("2").getDeletionTime());
        }
        finally
        {
            this.writer.deleteSecret(key);
        }
    }

    private static SecretProperties properties(String prefix)
    {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
            .withMapping(SecretProperties.class)
            .withSources(new PropertiesConfigSource(Map.of("wombat.secret.vault.path-prefix", prefix), "test", 100))
            .build();
        return config.getConfigMapping(SecretProperties.class);
    }

    public static class Profile extends AuthenticationEnabledTest.AuthenticationEnabledProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            Map<String, String> values = new HashMap<>(super.getConfigOverrides());
            values.put("wombat.secret.write-mode", SecretProperties.WriteMode.vault.name());
            values.put("wombat.secret.vault.path-prefix", VaultTestResource.PATH_PREFIX);
            return values;
        }
    }
}
