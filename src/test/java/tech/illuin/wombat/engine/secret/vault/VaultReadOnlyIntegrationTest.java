package tech.illuin.wombat.engine.secret.vault;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.secret.CompositeSecretResolver;
import tech.illuin.wombat.core.secret.EnvironmentSecretResolver;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.core.secret.SecretWriter;
import tech.illuin.wombat.engine.secret.SecretProperties;
import tech.illuin.wombat.engine.secret.database.DatabaseSecretStore;
import tech.illuin.wombat.security.PermissionAugmentor;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vault enabled next to the database writer, with an application token limited to the documented read-only policy. */
@QuarkusTest
@QuarkusTestResource(
    value = VaultTestResource.class,
    restrictToAnnotatedClass = true,
    initArgs = @ResourceArg(name = VaultTestResource.READ_ONLY, value = "true")
)
@TestProfile(VaultReadOnlyIntegrationTest.Profile.class)
class VaultReadOnlyIntegrationTest
{
    @Inject SecretResolver resolver;
    @Inject SecretWriter writer;
    @Inject VaultSecretResolver vault;

    private static final String TEST_KEK = Base64.getEncoder().encodeToString(
        "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
    );

    @Test
    void readOnlyVaultSitsBetweenTheWriterAndTheLocalSources()
    {
        List<SecretResolver> resolvers = assertInstanceOf(CompositeSecretResolver.class, this.resolver).resolvers();

        assertEquals(4, resolvers.size());
        assertSame(this.writer, resolvers.getFirst());
        assertInstanceOf(DatabaseSecretStore.class, this.writer);
        assertSame(this.vault, resolvers.get(1));
        assertInstanceOf(EnvironmentSecretResolver.class, resolvers.getLast());

        assertEquals("vault-shared", this.resolver.require("shared"));
        assertEquals("manually-provisioned", this.resolver.require("manual/seed"));
        assertEquals("directory-fallback", this.resolver.require("fallback-only"));
    }

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "email", value = "boss@example.com"))
    void theApiWritesToTheDatabaseAndChecksVaultKeys()
    {
        given().queryParam("key", "shared").when().get("/api/secret-keys/check")
            .then().statusCode(200).body("payload.exists", is(true));

        try
        {
            given().contentType(ContentType.JSON).body(Map.of("key", "shared", "value", "database-shared"))
                .when().put("/api/secrets").then().statusCode(200);
            assertEquals("database-shared", this.resolver.require("shared"));
            assertEquals("vault-shared", this.vault.require("shared"));

            List<String> listed = given().when().get("/api/secrets").then().statusCode(200)
                .extract().jsonPath().getList("payload.key", String.class);
            assertTrue(listed.contains("shared"));
            assertFalse(listed.contains("manual/seed"));

            given().queryParam("key", "shared").when().delete("/api/secrets").then().statusCode(200);
            assertEquals("vault-shared", this.resolver.require("shared"));
        }
        finally
        {
            this.writer.deleteSecret("shared");
        }
    }

    public static class Profile extends AuthenticationEnabledTest.AuthenticationEnabledProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            Map<String, String> values = new HashMap<>(super.getConfigOverrides());
            values.put("wombat.secret.write-mode", SecretProperties.WriteMode.database.name());
            values.put("wombat.secret.database.active-kek-id", "test");
            values.put("wombat.secret.database.kek.test", TEST_KEK);
            values.put("wombat.secret.vault.enabled", "true");
            values.put("wombat.secret.vault.path-prefix", VaultTestResource.PATH_PREFIX);
            return values;
        }
    }
}
