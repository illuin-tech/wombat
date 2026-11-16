package tech.illuin.wombat.persistence.backend.sqlite;

import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Produces;
import org.flywaydb.core.Flyway;
import tech.illuin.wombat.persistence.BackendMode;
import tech.illuin.wombat.persistence.PersistenceBackendProperties;
import tech.illuin.wombat.persistence.PersistenceBackendProperties.SQLiteProperties;
import tech.illuin.wombat.persistence.backend.api.*;
import tech.illuin.wombat.persistence.backend.api.store.MetricStoreContainer;
import tech.illuin.wombat.persistence.backend.api.action.FlywayMigrate;
import tech.illuin.wombat.persistence.backend.api.backup.BackupProperties;
import tech.illuin.wombat.persistence.backend.s3.S3Properties;
import tech.illuin.wombat.persistence.backend.sqlite.action.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static tech.illuin.wombat.persistence.backend.api.HookPhase.*;

@ApplicationScoped
public class SQLiteBackendConfig
{
    private static final String DBKIND_SQLITE = "sqlite";

    @Produces @Singleton
    public PersistenceBackend provideSQLiteBackend(
        PersistenceBackendProperties backendProps,
        Instance<AgroalDataSource> dataSources,
        Instance<Flyway> flyway,
        Instance<MeterRegistry> registry,
        MetricStoreContainer databaseStores
    ) {
        if (backendProps.mode() != BackendMode.sqlite)
            return PersistenceBackend.disabled(DBKIND_SQLITE);

        SQLiteProperties sqlite = backendProps.mode().getSection(backendProps);
        String jdbcUrl = sqlite.jdbcUrl();

        List<HookSupplier> hooks = new ArrayList<>();
        List<ProcessSupplier> processes = new ArrayList<>();

        String jdbcPath = jdbcUrl.substring("jdbc:sqlite:".length());

        hooks.add(new HookSupplier("flyway-migration", BACKEND_SETUP, 0, () -> new FlywayMigrate(flyway.get())));
        hooks.add(new HookSupplier("db-metrics", BACKEND_SETUP, 16, () -> new SQLiteDBMetricsInit(registry.get(), dataSources.get())));

        if (!jdbcPath.equals(":memory:"))
        {
            Path dbPath = Path.of(jdbcPath);

            hooks.add(new HookSupplier("directory-init", RESOURCE_INIT, 0, () -> new SQLiteDirectoryInit(dbPath)));

            sqlite.backup().filter(BackupProperties::enabled).ifPresent(backupProperties -> {
                S3Properties s3Properties = backupProperties.s3().orElseThrow(() -> new IllegalArgumentException("S3 properties must be provided when backup is enabled"));

                Supplier<Action> backupAction = () -> new SQLiteBackupProduce(dataSources.get(), s3Properties);
                processes.add(new ProcessSupplier("backup-produce", backupProperties.cron(), backupAction));
                hooks.add(new HookSupplier("backup-produce", BACKEND_TEARDOWN, backupAction));

                if (backupProperties.restoreOnStartup())
                    hooks.add(new HookSupplier("backup-restore", RESOURCE_INIT, 16, () -> new SQLiteBackupRestore(dbPath, s3Properties)));
                if (backupProperties.cleanup().enabled())
                    processes.add(new ProcessSupplier("backup-clean", backupProperties.cleanup().cron(), () -> new SQLiteBackupClean(backupProperties.cleanup(), s3Properties)));
            });
        }

        return PersistenceBackend.of(DBKIND_SQLITE, hooks, processes, databaseStores);
    }
}
