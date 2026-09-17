package tech.illuin.wombat.persistence.backend.sqlite;

import io.agroal.api.AgroalDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.flywaydb.core.Flyway;
import tech.illuin.wombat.persistence.backend.api.*;
import tech.illuin.wombat.persistence.backend.api.action.FlywayMigrate;
import tech.illuin.wombat.persistence.backend.s3.S3Properties;
import tech.illuin.wombat.persistence.backend.sqlite.action.*;
import tech.illuin.wombat.persistence.backend.api.backup.BackupProperties;

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
        @ConfigProperty(name = "quarkus.datasource.db-kind") String dbKind,
        @ConfigProperty(name = "quarkus.datasource.jdbc.url") String jdbcUrl,
        BackupProperties backupProperties,
        Instance<AgroalDataSource> dataSources,
        Instance<Flyway> flyway,
        Instance<MeterRegistry> registry
    ) {
        if (!dbKind.equals(DBKIND_SQLITE))
            return PersistenceBackend.disabled(DBKIND_SQLITE);

        List<HookSupplier> hooks = new ArrayList<>();
        List<ProcessSupplier> processes = new ArrayList<>();

        String jdbcPath = jdbcUrl.substring("jdbc:sqlite:".length());

        hooks.add(new HookSupplier("flyway-migration", BACKEND_SETUP, 0, () -> new FlywayMigrate(flyway.get())));
        hooks.add(new HookSupplier("db-metrics", BACKEND_SETUP, 16, () -> new SQLiteDBMetricsInit(registry.get(), dataSources.get())));

        if (!jdbcPath.equals(":memory:"))
        {
            Path dbPath = Path.of(jdbcPath);
            
            hooks.add(new HookSupplier("directory-init", RESOURCE_INIT, 0, () -> new SQLiteDirectoryInit(dbPath)));

            if (backupProperties.enabled())
            {
                S3Properties s3Properties = backupProperties.s3().orElseThrow(() -> new IllegalArgumentException("S3 properties must be provided when backup is enabled"));

                Supplier<Action> backupAction = () -> new SQLiteBackupProduce(dataSources.get(), s3Properties);
                processes.add(new ProcessSupplier("backup-produce", backupProperties.cron(), backupAction));
                hooks.add(new HookSupplier("backup-produce", BACKEND_TEARDOWN, backupAction));

                if (backupProperties.restoreOnStartup())
                    hooks.add(new HookSupplier("backup-restore", RESOURCE_INIT, 16, () -> new SQLiteBackupRestore(dbPath, s3Properties)));
                if (backupProperties.cleanup().enabled())
                    processes.add(new ProcessSupplier("backup-clean", backupProperties.cleanup().cron(), () -> new SQLiteBackupClean(backupProperties.cleanup(), s3Properties)));
            }
        }

        return PersistenceBackend.of(DBKIND_SQLITE, hooks, processes);
    }
}
