package tech.illuin.wombat.persistence.backend.postgres.action;

import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import tech.illuin.wombat.persistence.backend.api.Action;
import tech.illuin.wombat.persistence.backend.postgres.PostgresSizeGauge;

public class PostgresDBMetricsInit implements Action
{
    private final AgroalDataSource dataSource;
    private final MeterRegistry registry;

    public PostgresDBMetricsInit(MeterRegistry registry, AgroalDataSource dataSource)
    {
        this.registry = registry;
        this.dataSource = dataSource;
    }

    @Override
    public void run()
    {
        Gauge.builder("postgres.db.size.bytes", new PostgresSizeGauge(this.dataSource), PostgresSizeGauge::sizeBytes)
            .description("PostgreSQL database size in bytes")
            .register(this.registry);
    }
}
