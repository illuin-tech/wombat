package tech.illuin.wombat.engine.secret.database.crypto;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.secret.SecretProperties;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecretCryptographyTest
{
    private static final String SECRET_KEY = "api-token";
    private static final String SECRET_VALUE = "a-sensitive-secret-value";

    @Test
    void encryptionUsesFreshDekAndIndependentNoncesForEveryWrite()
    {
        SecretCryptography cryptography = cryptography("current", Map.of("current", key((byte) 1)));

        SecretCryptography.EncryptedSecret first = cryptography.encrypt(SECRET_KEY, SECRET_VALUE);
        SecretCryptography.EncryptedSecret second = cryptography.encrypt(SECRET_KEY, SECRET_VALUE);

        assertEquals("current", first.kekId());
        assertFalse(Arrays.equals(first.payloadNonce(), second.payloadNonce()));
        assertFalse(Arrays.equals(first.wrappingNonce(), second.wrappingNonce()));
        assertFalse(Arrays.equals(first.wrappedDek(), second.wrappedDek()));
        assertFalse(Arrays.equals(first.ciphertext(), second.ciphertext()));
        assertFalse(new String(first.ciphertext(), StandardCharsets.UTF_8).contains(SECRET_VALUE));
        assertFalse(new String(first.wrappedDek(), StandardCharsets.UTF_8).contains(SECRET_VALUE));
    }

    @Test
    void roundTripAuthenticatesSecretKeyAndEncryptedFields()
    {
        SecretCryptography cryptography = cryptography("current", Map.of("current", key((byte) 2)));
        SecretCryptography.EncryptedSecret encrypted = cryptography.encrypt(SECRET_KEY, SECRET_VALUE);

        assertEquals(SECRET_VALUE, cryptography.decrypt(encrypted));
        assertThrows(SecretIntegrityException.class, () -> cryptography.decrypt(withSecretKey(encrypted, "another-key")));
        assertThrows(SecretIntegrityException.class, () -> cryptography.decrypt(withCiphertext(encrypted, changed(encrypted.ciphertext()))));
        assertThrows(SecretIntegrityException.class, () -> cryptography.decrypt(withPayloadNonce(encrypted, changed(encrypted.payloadNonce()))));
        assertThrows(SecretIntegrityException.class, () -> cryptography.decrypt(withWrappedDek(encrypted, changed(encrypted.wrappedDek()))));
        assertThrows(SecretIntegrityException.class, () -> cryptography.decrypt(withWrappingNonce(encrypted, changed(encrypted.wrappingNonce()))));
    }

    @Test
    void wrongKekFailsClosed()
    {
        SecretCryptography writer = cryptography("current", Map.of("current", key((byte) 3)));
        SecretCryptography reader = cryptography("current", Map.of("current", key((byte) 4)));
        SecretCryptography.EncryptedSecret encrypted = writer.encrypt(SECRET_KEY, SECRET_VALUE);

        assertThrows(SecretIntegrityException.class, () -> reader.decrypt(encrypted));
    }

    @Test
    void rewrapChangesOnlyTheWrappedDekAndPreservesReadability()
    {
        String oldKey = key((byte) 5);
        String newKey = key((byte) 6);
        SecretCryptography oldCryptography = cryptography("old", Map.of("old", oldKey));
        SecretCryptography currentCryptography = cryptography("current", Map.of("old", oldKey, "current", newKey));
        SecretCryptography.EncryptedSecret encrypted = oldCryptography.encrypt(SECRET_KEY, SECRET_VALUE);

        SecretCryptography.RewrappedDek wrapped = currentCryptography.rewrap(encrypted);
        SecretCryptography.EncryptedSecret rewrapped = new SecretCryptography.EncryptedSecret(
            encrypted.secretKey(),
            wrapped.kekId(),
            encrypted.ciphertext(),
            encrypted.payloadNonce(),
            wrapped.wrappedDek(),
            wrapped.wrappingNonce()
        );
        assertEquals("current", wrapped.kekId());
        assertArrayEquals(encrypted.ciphertext(), rewrapped.ciphertext());
        assertArrayEquals(encrypted.payloadNonce(), rewrapped.payloadNonce());
        assertEquals(SECRET_VALUE, currentCryptography.decrypt(rewrapped));
    }

    private static SecretCryptography cryptography(String activeId, Map<String, String> keys)
    {
        Map<String, String> values = new HashMap<>();
        values.put("wombat.secret.write-mode", "DATABASE");
        values.put("wombat.secret.database.active-kek-id", activeId);
        keys.forEach((id, encoded) -> values.put("wombat.secret.database.kek." + id, encoded));
        SecretProperties properties = load(values);
        return new SecretCryptography(new LocalKeyEncryptionKeyProvider(properties));
    }

    private static String key(byte value)
    {
        byte[] key = new byte[32];
        Arrays.fill(key, value);
        return Base64.getEncoder().encodeToString(key);
    }

    private static byte[] changed(byte[] bytes)
    {
        byte[] changed = bytes.clone();
        changed[0] ^= 1;
        return changed;
    }

    private static SecretCryptography.EncryptedSecret withSecretKey(SecretCryptography.EncryptedSecret source, String secretKey)
    {
        return new SecretCryptography.EncryptedSecret(secretKey, source.kekId(), source.ciphertext(), source.payloadNonce(), source.wrappedDek(), source.wrappingNonce());
    }

    private static SecretCryptography.EncryptedSecret withCiphertext(SecretCryptography.EncryptedSecret source, byte[] ciphertext)
    {
        return new SecretCryptography.EncryptedSecret(source.secretKey(), source.kekId(), ciphertext, source.payloadNonce(), source.wrappedDek(), source.wrappingNonce());
    }

    private static SecretCryptography.EncryptedSecret withPayloadNonce(SecretCryptography.EncryptedSecret source, byte[] nonce)
    {
        return new SecretCryptography.EncryptedSecret(source.secretKey(), source.kekId(), source.ciphertext(), nonce, source.wrappedDek(), source.wrappingNonce());
    }

    private static SecretCryptography.EncryptedSecret withWrappedDek(SecretCryptography.EncryptedSecret source, byte[] wrappedDek)
    {
        return new SecretCryptography.EncryptedSecret(source.secretKey(), source.kekId(), source.ciphertext(), source.payloadNonce(), wrappedDek, source.wrappingNonce());
    }

    private static SecretCryptography.EncryptedSecret withWrappingNonce(SecretCryptography.EncryptedSecret source, byte[] nonce)
    {
        return new SecretCryptography.EncryptedSecret(source.secretKey(), source.kekId(), source.ciphertext(), source.payloadNonce(), source.wrappedDek(), nonce);
    }

    private static SecretProperties load(Map<String, String> values)
    {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
            .withMapping(SecretProperties.class)
            .withSources(new PropertiesConfigSource(values, "test", 100))
            .build();
        return config.getConfigMapping(SecretProperties.class);
    }
}