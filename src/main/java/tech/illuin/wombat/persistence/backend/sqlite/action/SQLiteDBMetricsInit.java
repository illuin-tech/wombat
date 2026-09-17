package tech.illuin.wombat.persistence.backend.sqlite.action;

import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.persistence.backend.api.Action;
import tech.illuin.wombat.persistence.backend.sqlite.SQLiteSizeGauge;

public class SQLiteDBMetricsInit implements Action
{
    private final AgroalDataSource dataSource;
    private final MeterRegistry registry;

    private static final Logger logger = LoggerFactory.getLogger(SQLiteDBMetricsInit.class);

    public SQLiteDBMetricsInit(MeterRegistry registry, AgroalDataSource dataSource)
    {
        this.registry = registry;
        this.dataSource = dataSource;
    }

    @Override
    public void run()
    {
        Gauge.builder("sqlite.db.size.bytes", new SQLiteSizeGauge(this.dataSource), SQLiteSizeGauge::sizeBytes)
            .description("SQLite database size in bytes")
            .register(this.registry);
    }
}
