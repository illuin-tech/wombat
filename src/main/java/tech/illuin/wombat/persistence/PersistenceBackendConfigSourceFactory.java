package tech.illuin.wombat.persistence;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.PropertiesConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static tech.illuin.wombat.commons.ConfigSourceFactories.BACKEND_MODE;

@StaticInitSafe
public class PersistenceBackendConfigSourceFactory implements ConfigSourceFactory
{
    public static final String PERSISTENCE_BACKEND_MODE_KEY = "persistence.backend.mode";
    public static final String ACTIVE_BACKEND_CONFIG_KEY = "_internal.persistence.backend";
    private static final Logger logger = LoggerFactory.getLogger(PersistenceBackendConfigSourceFactory.class);

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context)
    {
        String modeStr = context.getValue(PERSISTENCE_BACKEND_MODE_KEY).getValue();
        BackendMode activeMode = modeStr == null ? BackendMode.sqlite : parse(modeStr);

        Map<String, String> properties = new HashMap<>();
        properties.put(ACTIVE_BACKEND_CONFIG_KEY, activeMode.name());

        for (BackendMode mode : BackendMode.values())
            toggleProperties(properties, mode, activeMode == mode);
        mapProperties(context, properties, activeMode);

        logger.info("Persistence backend established: {}", activeMode);
        return List.of(new PropertiesConfigSource(
            properties,
            PersistenceBackendConfigSourceFactory.class.getSimpleName(),
            BACKEND_MODE.priority()
        ));
    }

    private static void toggleProperties(Map<String, String> properties, BackendMode mode, boolean enabled)
    {
        String state = Boolean.toString(enabled);
        properties.put("quarkus.datasource." + mode.name() + ".active", state);
        properties.put("quarkus.hibernate-orm." + mode.name() + ".active", state);
        properties.put("quarkus.hibernate-orm." + mode.name() + ".datasource", mode.name());
        properties.put("quarkus.hibernate-orm." + mode.name() + ".packages", "tech.illuin.wombat");
        properties.put("quarkus.hibernate-orm." + mode.name() + ".schema-management.strategy", "none");
        properties.put("quarkus.flyway." + mode.name() + ".active", state);
        properties.put("quarkus.flyway." + mode.name() + ".migrate-at-start", "false");
    }

    private static void mapProperties(ConfigSourceContext context, Map<String, String> properties, BackendMode mode)
    {
        mode.configMap().forEach((key, value) -> {
            String ctxValue = context.getValue(key).getValue();
            if (ctxValue != null)
                properties.put(value, ctxValue);
        });
    }

    private static BackendMode parse(String mode)
    {
        return BackendMode.valueOf(mode.trim().replace('-', '_').toLowerCase(Locale.ROOT));
    }

    @Override
    public OptionalInt getPriority()
    {
        return OptionalInt.of(BACKEND_MODE.priority());
    }
}
