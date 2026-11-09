package tech.illuin.wombat.engine.secret.database;

import io.quarkus.arc.All;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.secret.CompositeSecretResolver;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.core.secret.SecretWriter;
import tech.illuin.wombat.engine.secret.*;
import tech.illuin.wombat.engine.secret.database.persistence.SecretEntity;
import tech.illuin.wombat.engine.secret.database.persistence.SecretRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(DatabaseSecretStoreTest.Profile.class)
class DatabaseSecretStoreTest
{
    @Inject DatabaseSecretStore secrets;
    @Inject SecretRepository repository;
    @Inject SecretResolver resolver;
    @Inject SecretWriter writer;
    @Inject EntityManager entityManager;
    @Inject @WritableBackend
    SecretConfig.SecretResolverCandidate electedBackend;
    @Inject @All
    List<SecretConfig.SecretResolverCandidate> resolverCandidates;

    private final String key = "store-test-" + UUID.randomUUID();

    @Test
    @Transactional
    void createsReadsUpdatesAndPermanentlyDeletesEncryptedSecrets()
    {
        String plaintext = "database-secret-value";
        this.secrets.putSecret(this.key, plaintext);
        this.entityManager.flush();
        this.entityManager.clear();

        SecretEntity created = this.repository.findById(this.key);
        assertNotNull(created);
        assertEquals(plaintext, this.secrets.require(this.key));
        assertEquals(plaintext, this.resolver.find(this.key).orElseThrow());
        assertFalse(new String(created.ciphertext, StandardCharsets.UTF_8).contains(plaintext));
        assertFalse(new String(created.wrappedDek, StandardCharsets.UTF_8).contains(plaintext));
        assertEquals(0, created.revision);

        this.secrets.putSecret(this.key, "updated-secret-value");
        this.entityManager.flush();
        this.entityManager.clear();
        assertEquals("updated-secret-value", this.secrets.find(this.key).orElseThrow());
        assertEquals(1, this.repository.findById(this.key).revision);

        assertTrue(this.secrets.deleteSecret(this.key));
        assertFalse(this.secrets.deleteSecret(this.key));
        assertTrue(this.secrets.find(this.key).isEmpty());
    }

    @Test
    void rejectsNullOrBlankKeysAndValues()
    {
        assertThrows(IllegalArgumentException.class, () -> this.secrets.find(null));
        assertThrows(IllegalArgumentException.class, () -> this.secrets.find("  "));
        assertThrows(IllegalArgumentException.class, () -> this.secrets.putSecret(null, "value"));
        assertThrows(IllegalArgumentException.class, () -> this.secrets.putSecret("key", " "));
        assertThrows(IllegalArgumentException.class, () -> this.secrets.deleteSecret(""));
    }

    @Test
    void selectedDatabaseIsFirstResolverAndSharedWriter()
    {
        List<SecretResolver> electedResolvers = this.resolverCandidates.stream()
            .filter(SecretConfig.SecretResolverCandidate::isElected)
            .map(SecretConfig.SecretResolverCandidate::resolver)
            .toList();

        assertTrue(this.electedBackend.isElected());
        assertSame(this.secrets, this.electedBackend.resolver());
        assertSame(this.secrets, this.writer);
        assertSame(this.secrets, electedResolvers.getFirst());
        assertEquals(electedResolvers, ((CompositeSecretResolver)this.resolver).resolvers());
    }

    @AfterEach
    @Transactional
    void cleanUp()
    {
        this.secrets.deleteSecret(this.key);
    }

    public static class Profile implements QuarkusTestProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            String testKek = java.util.Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
            );
            return Map.of(
                "wombat.secret.write-mode", "DATABASE",
                "wombat.secret.database.active-kek-id", "test",
                "wombat.secret.database.kek.test", testKek
            );
        }
    }
}