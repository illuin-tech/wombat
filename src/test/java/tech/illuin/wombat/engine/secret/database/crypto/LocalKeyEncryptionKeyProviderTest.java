package tech.illuin.wombat.engine.secret.database.crypto;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.secret.SecretProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalKeyEncryptionKeyProviderTest
{
    @Test
    void rejectsEnabledStoreWithoutAnActiveKek()
    {
        assertThrows(IllegalArgumentException.class, () -> new LocalKeyEncryptionKeyProvider(load(Map.of(
            "wombat.secret.write-mode", "DATABASE"
        ))));
    }

    @Test
    void doesNotValidateKeksWhenDatabaseModeIsDisabled()
    {
        LocalKeyEncryptionKeyProvider provider = new LocalKeyEncryptionKeyProvider(load(Map.of(
            "wombat.secret.database.active-kek-id", "missing",
            "wombat.secret.database.kek.invalid", "not-base64!"
        )));

        assertThrows(IllegalStateException.class, provider::activeKeyId);
    }

    @Test
    void rejectsInvalidBase64WrongLengthAndMissingActiveKey()
    {
        assertThrows(IllegalArgumentException.class, () -> new LocalKeyEncryptionKeyProvider(load(Map.of(
            "wombat.secret.write-mode", "DATABASE",
            "wombat.secret.database.active-kek-id", "current",
            "wombat.secret.database.kek.current", "not-base64!"
        ))));
        assertThrows(IllegalArgumentException.class, () -> new LocalKeyEncryptionKeyProvider(load(Map.of(
            "wombat.secret.write-mode", "DATABASE",
            "wombat.secret.database.active-kek-id", "current",
            "wombat.secret.database.kek.current", "c2hvcnQ="
        ))));
        assertThrows(IllegalArgumentException.class, () -> new LocalKeyEncryptionKeyProvider(load(Map.of(
            "wombat.secret.write-mode", "DATABASE",
            "wombat.secret.database.active-kek-id", "current",
            "wombat.secret.database.kek.previous", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        ))));
    }

    @Test
    void returnsClonedConfiguredKeyBytesAndRejectsUnknownIds()
    {
        byte[] expected = new byte[32];
        String encoded = java.util.Base64.getEncoder().encodeToString(expected);
        LocalKeyEncryptionKeyProvider provider = new LocalKeyEncryptionKeyProvider(load(Map.of(
            "wombat.secret.write-mode", "DATABASE",
            "wombat.secret.database.active-kek-id", "current",
            "wombat.secret.database.kek.current", encoded
        )));

        assertArrayEquals(expected, provider.key("current"));
        byte[] returned = provider.key("current");
        returned[0] = 1;
        assertArrayEquals(expected, provider.key("current"));
        assertThrows(IllegalStateException.class, () -> provider.key("unknown"));
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