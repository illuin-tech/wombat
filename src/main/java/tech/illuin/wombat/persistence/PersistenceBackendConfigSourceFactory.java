package tech.illuin.wombat.persistence;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.PropertiesConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@StaticInitSafe
public class PersistenceBackendConfigSourceFactory implements ConfigSourceFactory
{
    public static final String ACTIVE_BACKEND = "_internal.persistence.backend";
    private static final Logger logger = LoggerFactory.getLogger(PersistenceBackendConfigSourceFactory.class);

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context)
    {
        String modeStr = context.getValue("persistence.backend.mode").getValue();
        BackendMode mode = modeStr == null ? BackendMode.sqlite : parse(modeStr);

        Map<String, String> properties = new HashMap<>();
        properties.put(ACTIVE_BACKEND, mode.name().toLowerCase(Locale.ROOT));

        switch (mode)
        {
            case sqlite:
                properties.put("quarkus.datasource.db-kind", "sqlite");
                String sqliteUrl = context.getValue("persistence.backend.sqlite.jdbc-url").getValue();
                if (sqliteUrl != null)
                    properties.put("quarkus.datasource.jdbc.url", sqliteUrl);
                properties.put("quarkus.flyway.locations", "classpath:db/migration/sqlite");
                break;

            case postgresql:
                properties.put("quarkus.datasource.db-kind", "postgresql");
                String pgUrl = context.getValue("persistence.backend.postgresql.jdbc-url").getValue();
                if (pgUrl != null)
                    properties.put("quarkus.datasource.jdbc.url", pgUrl);
                String pgUser = context.getValue("persistence.backend.postgresql.username").getValue();
                if (pgUser != null)
                    properties.put("quarkus.datasource.username", pgUser);
                String pgPass = context.getValue("persistence.backend.postgresql.password").getValue();
                if (pgPass != null)
                    properties.put("quarkus.datasource.password", pgPass);
                properties.put("quarkus.flyway.locations", "classpath:db/migration/postgresql");
                break;

            default:
                throw new IllegalArgumentException("Unknown backend mode: " + mode);
        }

        logger.info("Persistence backend set to {} (priority {})", mode, Integer.MAX_VALUE - 1000);
        return List.of(new PropertiesConfigSource(
            properties,
            PersistenceBackendConfigSourceFactory.class.getSimpleName(),
            Integer.MAX_VALUE - 1000
        ));
    }

    private static BackendMode parse(String mode)
    {
        return BackendMode.valueOf(mode.trim().replace('-', '_').toLowerCase(Locale.ROOT));
    }
}
