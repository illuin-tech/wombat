package tech.illuin.wombat.persistence;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.illuin.wombat.core.source.persistence.micrometer.MicrometerMetricPersister;
import tech.illuin.wombat.persistence.dialect.JsonPathDialect;

@ApplicationScoped
public class PersistenceConfig
{
    private static final String DBKIND_SQLITE = "sqlite";
    private static final String DBKIND_POSTGRESQL = "postgresql";

    @Singleton
    @IfBuildProperty(name = "persistence.enable-micrometer-metric-persister", stringValue = "true")
    public MicrometerMetricPersister provideMicrometerMetricPersister(MeterRegistry meterRegistry)
    {
        return new MicrometerMetricPersister(meterRegistry);
    }

    @Singleton
    public JsonPathDialect provideJsonPathDialect(@ConfigProperty(name = "quarkus.datasource.db-kind") String dbKind)
    {
        return switch (dbKind)
        {
            case DBKIND_SQLITE -> JsonPathDialect.SQLITE;
            case DBKIND_POSTGRESQL -> JsonPathDialect.POSTGRESQL;
            default -> throw new IllegalArgumentException("No JSON path dialect for db-kind: " + dbKind);
        };
    }
}
