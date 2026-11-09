package tech.illuin.wombat.engine.secret;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretPropertiesTest
{
    @Test
    void writeModeDefaultsToDisabledAndResolverPropertiesKeepTheirDefaults()
    {
        SecretProperties properties = load(Map.of());

        assertTrue(properties.directory().path().isEmpty());
        assertTrue(properties.keystore().path().isEmpty());
        assertTrue(properties.keystore().password().isEmpty());
        assertEquals("PKCS12", properties.keystore().type());
        assertEquals(SecretProperties.WriteMode.disabled, properties.writeMode());
        assertTrue(properties.database().activeKekId().isEmpty());
        assertTrue(properties.database().kek().isEmpty());
    }

    @Test
    void mapsResolverPropertiesAndExternalDatabaseKekConfiguration()
    {
        SecretProperties properties = load(Map.of(
            "wombat.secret.directory.path", "/secrets",
            "wombat.secret.keystore.path", "/secrets/keystore.p12",
            "wombat.secret.keystore.password", "store-password",
            "wombat.secret.keystore.type", "JKS",
            "wombat.secret.write-mode", "DATABASE",
            "wombat.secret.database.active-kek-id", "current",
            "wombat.secret.database.kek.current", "Y3VycmVudC1rZXk=",
            "wombat.secret.database.kek.previous", "cHJldmlvdXMta2V5"
        ));

        assertEquals("/secrets", properties.directory().path().orElseThrow());
        assertEquals("/secrets/keystore.p12", properties.keystore().path().orElseThrow());
        assertEquals("store-password", properties.keystore().password().orElseThrow());
        assertEquals("JKS", properties.keystore().type());
        assertEquals(SecretProperties.WriteMode.database, properties.writeMode());
        assertEquals("current", properties.database().activeKekId().orElseThrow());
        assertEquals(Map.of("current", "Y3VycmVudC1rZXk=", "previous", "cHJldmlvdXMta2V5"), properties.database().kek());
    }

    @Test
    void rejectsUnknownWriteModes()
    {
        assertThrows(RuntimeException.class, () -> load(Map.of("wombat.secret.write-mode", "VAULT")));
        assertThrows(RuntimeException.class, () -> load(Map.of("wombat.secret.write-mode", "disabled")));
        assertEquals(SecretProperties.WriteMode.disabled, load(Map.of("wombat.secret.write-mode", "DISABLED")).writeMode());
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