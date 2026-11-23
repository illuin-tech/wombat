package tech.illuin.wombat.feature;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureConfigSourceFactoryTest
{
    @Test
    void secretAdministrationNeedsAWriteMode()
    {
        assertEquals("false", adminSecrets("standalone", null));
        assertEquals("false", adminSecrets("standalone", ""));
        assertEquals("false", adminSecrets("standalone", "disabled"));
        assertEquals("false", adminSecrets("standalone", "DISABLED"));
        assertEquals("true", adminSecrets("standalone", "database"));
        assertEquals("true", adminSecrets("standalone", "VAULT"));
    }

    @Test
    void workersServeNoSecretAdministration()
    {
        assertEquals("false", adminSecrets("worker", "database"));
    }

    private static String adminSecrets(String nodeMode, String writeMode)
    {
        Map<String, String> values = new HashMap<>();
        values.put("wombat.mode", nodeMode);
        values.put(FeatureConfigSourceFactory.SECRET_WRITE_MODE, writeMode);

        ConfigSourceContext context = mock(ConfigSourceContext.class);
        values.forEach((name, value) -> when(context.getValue(name)).thenReturn(
            ConfigValue.builder().withName(name).withValue(value).build()
        ));

        ConfigSource source = new FeatureConfigSourceFactory().getConfigSources(context).iterator().next();
        return source.getValue(Feature.Keys.ADMIN_SECRETS);
    }
}
