package tech.illuin.wombat.commons;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.PropertiesConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@StaticInitSafe
public class NodeModeConfigSourceFactory implements ConfigSourceFactory
{
    public static final String WORKER_DB_KIND = "sqlite";
    private static final String WORKER_JDBC_URL = "jdbc:sqlite::memory:";

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context)
    {
        ConfigValue value = context.getValue("wombat.mode");
        NodeMode mode = value.getValue() == null ? null : parse(value.getValue());

        Map<String, String> properties = new HashMap<>();
        if (mode == NodeMode.WORKER)
        {
            String dbKind = context.getValue("quarkus.datasource.db-kind").getValue();
            if (dbKind != null && !dbKind.equals(WORKER_DB_KIND))
                throw new IllegalStateException("Worker mode only runs on SQLite, got quarkus.datasource.db-kind: " + dbKind);

            properties.put("quarkus.datasource.jdbc.url", WORKER_JDBC_URL);
            properties.put("quarkus.datasource.jdbc.min-size", "1");
            properties.put("quarkus.datasource.jdbc.max-size", "1");
        }

        return List.of(new PropertiesConfigSource(
            properties,
            NodeModeConfigSourceFactory.class.getSimpleName(),
            Integer.MAX_VALUE
        ));
    }

    private static NodeMode parse(String mode)
    {
        return NodeMode.valueOf(mode.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }
}
