package tech.illuin.wombat.commons;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.PropertiesConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static tech.illuin.wombat.persistence.PersistenceBackendConfigSourceFactory.ACTIVE_BACKEND_CONFIG_KEY;

@StaticInitSafe
public class NodeModeConfigSourceFactory implements ConfigSourceFactory
{
    public static final String AGGREGATOR_CONFIG_KEY = "_internal.aggregator";
    private static final String WORKER_DB_KIND = "sqlite";
    private static final String WORKER_JDBC_URL = "jdbc:sqlite::memory:";

    private static final Logger logger = LoggerFactory.getLogger(NodeModeConfigSourceFactory.class);

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context)
    {
        ConfigValue value = context.getValue("wombat.mode");
        NodeMode mode = value.getValue() == null ? null : parse(value.getValue());

        Map<String, String> properties = new HashMap<>();
        properties.put(AGGREGATOR_CONFIG_KEY, String.valueOf(mode == NodeMode.aggregator));

        if (mode == NodeMode.worker)
        {
            String backend = context.getValue(ACTIVE_BACKEND_CONFIG_KEY).getValue();
            if (backend != null && !backend.equals(WORKER_DB_KIND))
                logger.warn("Worker mode only runs on SQLite in-memory: overriding backend {} with {}", backend, WORKER_DB_KIND);

            properties.put("quarkus.datasource.db-kind", WORKER_DB_KIND);
            properties.put("quarkus.datasource.jdbc.url", WORKER_JDBC_URL);
            properties.put("quarkus.datasource.jdbc.min-size", "1");
            properties.put("quarkus.datasource.jdbc.max-size", "1");
            properties.put(ACTIVE_BACKEND_CONFIG_KEY, WORKER_DB_KIND);
        }

        return List.of(new PropertiesConfigSource(
            properties,
            NodeModeConfigSourceFactory.class.getSimpleName(),
            Integer.MAX_VALUE
        ));
    }

    private static NodeMode parse(String mode)
    {
        return NodeMode.valueOf(mode.trim().replace('-', '_').toLowerCase());
    }
}
