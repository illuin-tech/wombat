package tech.illuin.wombat.persistence.backend.postgres;

import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Produces;
import org.flywaydb.core.Flyway;
import tech.illuin.wombat.persistence.BackendMode;
import tech.illuin.wombat.persistence.PersistenceBackendProperties;
import tech.illuin.wombat.persistence.backend.api.*;
import tech.illuin.wombat.persistence.backend.api.store.MetricStoreContainer;
import tech.illuin.wombat.persistence.backend.api.action.FlywayMigrate;
import tech.illuin.wombat.persistence.backend.postgres.action.*;

import java.util.ArrayList;
import java.util.List;

import static tech.illuin.wombat.persistence.backend.api.HookPhase.*;

@ApplicationScoped
public class PostgresBackendConfig
{
    private static final String DBKIND_POSTGRESQL = "postgresql";

    @Produces @Singleton
    public PersistenceBackend providePostgresBackend(
        PersistenceBackendProperties backendProps,
        Instance<AgroalDataSource> dataSources,
        Instance<Flyway> flyway,
        Instance<MeterRegistry> registry,
        MetricStoreContainer databaseStores
    ) {
        if (backendProps.mode() != BackendMode.postgresql)
            return PersistenceBackend.disabled(DBKIND_POSTGRESQL);

        List<HookSupplier> hooks = new ArrayList<>();

        hooks.add(new HookSupplier("flyway-migration", BACKEND_SETUP, 0, () -> new FlywayMigrate(flyway.get())));
        hooks.add(new HookSupplier("db-metrics", BACKEND_SETUP, 16, () -> new PostgresDBMetricsInit(registry.get(), dataSources.get())));

        return PersistenceBackend.of(DBKIND_POSTGRESQL, hooks, List.of(), databaseStores);
    }
}
