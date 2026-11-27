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

    @Test
    void workerEnvironmentsApiIsServedOnlyByAWorkerSetSo()
    {
        assertEquals("true", workerEnvironments("worker", "true"));
        assertEquals("true", workerEnvironments("worker", " TRUE "));
        assertEquals("false", workerEnvironments("worker", "false"));
        assertEquals("false", workerEnvironments("worker", null));
        assertEquals("false", workerEnvironments("standalone", "true"));
        assertEquals("false", workerEnvironments("aggregator", "true"));
    }

    private static String adminSecrets(String nodeMode, String writeMode)
    {
        return feature(Feature.Keys.ADMIN_SECRETS, nodeMode, writeMode, null);
    }

    private static String workerEnvironments(String nodeMode, String api)
    {
        return feature(Feature.Keys.WORKER_ENVIRONMENTS, nodeMode, null, api);
    }

    /** Every setting the factory reads is set, possibly to null as when it is missing. */
    private static String feature(String key, String nodeMode, String writeMode, String workerEnvironmentsApi)
    {
        Map<String, String> values = new HashMap<>();
        values.put("wombat.mode", nodeMode);
        values.put(FeatureConfigSourceFactory.SECRET_WRITE_MODE, writeMode);
        values.put(FeatureConfigSourceFactory.WORKER_ENVIRONMENTS_API, workerEnvironmentsApi);

        ConfigSourceContext context = mock(ConfigSourceContext.class);
        values.forEach((name, value) -> when(context.getValue(name)).thenReturn(
            ConfigValue.builder().withName(name).withValue(value).build()
        ));

        ConfigSource source = new FeatureConfigSourceFactory().getConfigSources(context).iterator().next();
        return source.getValue(key);
    }
}
