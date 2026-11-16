package tech.illuin.wombat.persistence;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import tech.illuin.wombat.core.source.persistence.WombatMetricPersister;
import tech.illuin.wombat.core.source.persistence.micrometer.MicrometerMetricPersister;
import tech.illuin.wombat.persistence.dialect.JsonPathDialect;

@ApplicationScoped
public class PersistenceConfig
{
    @Singleton
    public WombatMetricPersister provideMicrometerMetricPersister(MeterRegistry meterRegistry)
    {
        return new MicrometerMetricPersister(meterRegistry);
    }

    @Singleton
    public JsonPathDialect provideJsonPathDialect(PersistenceBackendProperties backendProps)
    {
        return switch (backendProps.mode())
        {
            case sqlite -> JsonPathDialect.SQLITE;
            case postgresql -> JsonPathDialect.POSTGRESQL;
        };
    }
}
