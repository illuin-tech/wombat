package tech.illuin.wombat.engine.secret.database.crypto;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.secret.database.DatabaseSecretStore;
import tech.illuin.wombat.engine.secret.database.persistence.SecretEntity;
import tech.illuin.wombat.engine.secret.database.persistence.SecretRepository;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(SecretRewrapServiceTest.Profile.class)
class SecretRewrapServiceTest
{
    @Inject SecretRepository repository;
    @Inject SecretRewrapService rewrapService;
    @Inject DatabaseSecretStore secrets;

    private final Set<String> createdKeys = new HashSet<>();

    private static final String OLD_KEK = encodedKey("old-0123456789012345678901234567");

    @Test
    void rewrapsOnlyTheDekAndPreservesSecretReadability()
    {
        String key = createOldSecret("rewrap-readable", "still-readable");
        SecretEntity before = find(key);
        assertEquals("still-readable", this.secrets.require(key));

        SecretRewrap result = this.rewrapService.rewrapBatch();
        SecretEntity after = find(key);

        assertEquals(1, result.scanned());
        assertEquals(1, result.rewrapped());
        assertEquals(0, result.failures());
        assertEquals(0, result.conflicts());
        assertEquals(0, result.remaining());
        assertTrue(result.complete());
        assertEquals("current", after.kekId);
        assertEquals(before.revision + 1, after.revision);
        assertArrayEquals(before.ciphertext, after.ciphertext);
        assertArrayEquals(before.payloadNonce, after.payloadNonce);
        assertFalse(Arrays.equals(before.wrappedDek, after.wrappedDek));
        assertFalse(Arrays.equals(before.wrappingNonce, after.wrappingNonce));
        assertEquals("still-readable", this.secrets.require(key));

        SecretRewrap repeated = this.rewrapService.rewrapBatch();
        assertEquals(0, repeated.scanned());
        assertEquals(0, repeated.rewrapped());
        assertTrue(repeated.complete());
    }

    @Test
    void rewrapDoesNotDecryptTheSecretPayload()
    {
        String key = createOldSecret("rewrap-corrupt-payload", "unreadable-after-tampering");
        QuarkusTransaction.requiringNew().run(() -> {
            SecretEntity entity = this.repository.findById(key);
            entity.ciphertext[0] ^= 1;
        });

        SecretRewrap result = this.rewrapService.rewrapBatch();

        assertEquals(1, result.rewrapped());
        assertEquals(0, result.failures());
        assertThrows(SecretIntegrityException.class, () -> this.secrets.find(key));
    }

    @Test
    void reportsMissingOldKeysAndTamperedWrappedDeksWithoutLosingRows()
    {
        String unknownKekKey = createOldSecret("rewrap-unknown-kek", "value-one");
        String tamperedDekKey = createOldSecret("rewrap-tampered-dek", "value-two");
        String healthyKey = createOldSecret("rewrap-zz-healthy", "value-three");
        QuarkusTransaction.requiringNew().run(() -> {
            SecretEntity missingKey = this.repository.findById(unknownKekKey);
            missingKey.kekId = "missing-key";
            SecretEntity tamperedDek = this.repository.findById(tamperedDekKey);
            tamperedDek.wrappedDek[0] ^= 1;
        });

        SecretRewrap result = this.rewrapService.rewrapBatch();

        assertEquals(3, result.scanned());
        assertEquals(1, result.rewrapped());
        assertEquals(2, result.failures());
        assertEquals(2, result.remaining());
        assertFalse(result.complete());
        assertEquals("missing-key", find(unknownKekKey).kekId);
        assertEquals("old", find(tamperedDekKey).kekId);
        assertEquals("current", find(healthyKey).kekId);
        assertEquals("value-three", this.secrets.require(healthyKey));
    }

    @Test
    void commitsBoundedBatchesAndCanResumeAfterAnOperationBoundary()
    {
        List<SecretEntity> rows = new ArrayList<>();
        for (int i = 0; i < SecretRewrapService.BATCH_SIZE + 1; i++)
        {
            String key = "rewrap-batch-" + i + "-" + UUID.randomUUID();
            this.createdKeys.add(key);
            rows.add(oldSecret(key, "batch-value-" + i));
        }
        QuarkusTransaction.requiringNew().run(() -> rows.forEach(this.repository::persist));

        SecretRewrap first = this.rewrapService.rewrapBatch(1);
        assertEquals(SecretRewrapService.BATCH_SIZE, first.scanned());
        assertEquals(SecretRewrapService.BATCH_SIZE, first.rewrapped());
        assertEquals(1, first.remaining());
        assertFalse(first.complete());
        assertEquals(1, QuarkusTransaction.requiringNew().call(() -> this.repository.countNeedingRewrap("current")));

        Thread.currentThread().interrupt();
        try {
            assertThrows(IllegalStateException.class, () -> this.rewrapService.rewrapBatch(1));
        }
        finally {
            Thread.interrupted();
        }

        SecretRewrap resumed = this.rewrapService.rewrapBatch(1);
        assertEquals(1, resumed.scanned());
        assertEquals(1, resumed.rewrapped());
        assertEquals(0, resumed.remaining());
        assertTrue(resumed.complete());
    }

    @AfterEach
    void cleanUp()
    {
        QuarkusTransaction.requiringNew().run(() -> {
            for (String key : this.createdKeys)
                this.repository.deleteById(key);
        });
    }

    private String createOldSecret(String key, String value)
    {
        this.createdKeys.add(key);
        QuarkusTransaction.requiringNew().run(() -> this.repository.persist(oldSecret(key, value)));
        return key;
    }

    private SecretEntity find(String key)
    {
        return QuarkusTransaction.requiringNew().call(() -> this.repository.findById(key));
    }

    private static SecretEntity oldSecret(String key, String value)
    {
        SecretCryptography.EncryptedSecret encrypted = new SecretCryptography(oldKeyProvider()).encrypt(key, value);
        SecretEntity entity = new SecretEntity();
        entity.key = key;
        entity.ciphertext = encrypted.ciphertext();
        entity.payloadNonce = encrypted.payloadNonce();
        entity.wrappedDek = encrypted.wrappedDek();
        entity.wrappingNonce = encrypted.wrappingNonce();
        entity.kekId = encrypted.kekId();
        return entity;
    }

    private static KeyEncryptionKeyProvider oldKeyProvider()
    {
        return new KeyEncryptionKeyProvider() {
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
    }

    private static String encodedKey(String material)
    {
        return Base64.getEncoder().encodeToString(material.getBytes(StandardCharsets.UTF_8));
    }

    public static class Profile implements QuarkusTestProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            return Map.of(
                "wombat.secret.write-mode", "DATABASE",
                "wombat.secret.database.active-kek-id", "current",
                "wombat.secret.database.kek.current", encodedKey("new-0123456789012345678901234567"),
                "wombat.secret.database.kek.old", OLD_KEK
            );
        }
    }
}