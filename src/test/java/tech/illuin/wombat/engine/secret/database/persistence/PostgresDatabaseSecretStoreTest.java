package tech.illuin.wombat.engine.secret.database.persistence;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.secret.database.DatabaseSecretStore;
import tech.illuin.wombat.engine.secret.database.crypto.KeyEncryptionKeyProvider;
import tech.illuin.wombat.engine.secret.database.crypto.SecretCryptography;
import tech.illuin.wombat.engine.secret.database.crypto.SecretRewrap;
import tech.illuin.wombat.engine.secret.database.crypto.SecretRewrapService;
import tech.illuin.wombat.persistence.backend.postgres.PostgresTestContainer;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(PostgresDatabaseSecretStoreTest.Profile.class)
class PostgresDatabaseSecretStoreTest
{
    @Inject DatabaseSecretStore secrets;
    @Inject SecretRepository repository;
    @Inject EntityManager entityManager;
    @Inject SecretCryptography cryptography;
    @Inject SecretRewrapService rewrapService;

    private static final String OLD_KEK = Base64.getEncoder().encodeToString(
        "old-0123456789012345678901234567".getBytes(StandardCharsets.UTF_8)
    );

    @Test
    @Transactional
    void persistsEncryptedSecretUsingPostgresBinaryColumns()
    {
        String key = "postgres-secret-" + UUID.randomUUID();
        String value = "postgres-secret-value";

        this.secrets.putSecret(key, value);
        this.entityManager.flush();
        this.entityManager.clear();

        SecretEntity stored = this.repository.findById(key);
        assertNotNull(stored);
        assertNotNull(stored.ciphertext);
        assertEquals(value, this.secrets.require(key));
        assertEquals("test", stored.kekId);
        this.secrets.deleteSecret(key);
    }


    @Test
    void rewrapsPostgresRowsWithoutChangingPayload()
    {
        String key = "postgres-rewrap-" + UUID.randomUUID();
        SecretCryptography.EncryptedSecret encrypted = oldSecret(key, "postgres-rewrap-value");
        persist(encrypted);
        try
        {
            SecretRewrap result = this.rewrapService.rewrapBatch();
            SecretEntity stored = find(key);
            assertEquals(1, result.rewrapped());
            assertEquals(0, result.remaining());
            assertEquals("test", stored.kekId);
            assertArrayEquals(encrypted.ciphertext(), stored.ciphertext);
            assertArrayEquals(encrypted.payloadNonce(), stored.payloadNonce);
            assertEquals("postgres-rewrap-value", this.secrets.require(key));
        }
        finally {
            QuarkusTransaction.requiringNew().run(() -> this.secrets.deleteSecret(key));
        }
    }

    @Test
    void staleRewrapCannotOverwriteAConcurrentCrudUpdate()
    {
        String key = "postgres-stale-rewrap-" + UUID.randomUUID();
        SecretCryptography.EncryptedSecret encrypted = oldSecret(key, "old-value");
        persist(encrypted);
        try
        {
            long staleRevision = find(key).revision;
            SecretCryptography.RewrappedDek wrapped = this.cryptography.rewrap(encrypted);
            this.secrets.putSecret(key, "new-value");

            int updated = QuarkusTransaction.requiringNew().call(() -> this.repository.updateWrappedDekIfRevisionMatches(
                key, staleRevision, wrapped.kekId(), wrapped.wrappedDek(), wrapped.wrappingNonce()
            ));
            assertEquals(0, updated);
            assertEquals("new-value", this.secrets.require(key));
        }
        finally {
            QuarkusTransaction.requiringNew().run(() -> this.secrets.deleteSecret(key));
        }
    }

    private void persist(SecretCryptography.EncryptedSecret encrypted)
    {
        SecretEntity entity = new SecretEntity();
        entity.key = encrypted.secretKey();
        entity.kekId = encrypted.kekId();
        entity.ciphertext = encrypted.ciphertext();
        entity.payloadNonce = encrypted.payloadNonce();
        entity.wrappedDek = encrypted.wrappedDek();
        entity.wrappingNonce = encrypted.wrappingNonce();
        QuarkusTransaction.requiringNew().run(() -> this.repository.persist(entity));
    }

    private SecretEntity find(String key)
    {
        return QuarkusTransaction.requiringNew().call(() -> this.repository.findById(key));
    }

    private static SecretCryptography.EncryptedSecret oldSecret(String key, String value)
    {
        KeyEncryptionKeyProvider provider = new KeyEncryptionKeyProvider() {
            @Override
            public String activeKeyId()
            {
                return "old";
            }

            @Override
            public byte[] key(String id)
            {
                if (!id.equals("old"))
                    throw new IllegalStateException("Unknown test KEK");
                return Base64.getDecoder().decode(OLD_KEK);
            }
        };
        return new SecretCryptography(provider).encrypt(key, value);
    }


    public static class Profile implements QuarkusTestProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            String jdbcUrl;
            try {
                jdbcUrl = PostgresTestContainer.freshDatabase("wombat_secret_store_test");
            }
            catch (SQLException e) {
                throw new IllegalStateException("Could not create PostgreSQL test database", e);
            }
            String testKek = java.util.Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
            );
            return Map.ofEntries(
                Map.entry("quarkus.datasource.db-kind", "postgresql"),
                Map.entry("quarkus.datasource.username", PostgresTestContainer.username()),
                Map.entry("quarkus.datasource.password", PostgresTestContainer.password()),
                Map.entry("quarkus.datasource.jdbc.url", jdbcUrl),
                Map.entry("quarkus.flyway.locations", "classpath:db/migration/postgresql"),
                Map.entry("quarkus.flyway.migrate-at-start", "true"),
                Map.entry("quarkus.flyway.clean-at-start", "true"),
                Map.entry("quarkus.flyway.clean-disabled", "false"),
                Map.entry("wombat.secret.write-mode", "DATABASE"),
                Map.entry("wombat.secret.database.active-kek-id", "test"),
                Map.entry("wombat.secret.database.kek.test", testKek),
                Map.entry("wombat.secret.database.kek.old", OLD_KEK)
            );
        }
    }
}