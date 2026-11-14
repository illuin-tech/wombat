package tech.illuin.wombat.persistence;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import tech.illuin.wombat.persistence.backend.api.backup.BackupProperties;

import java.util.Optional;

@ConfigMapping(prefix = "persistence.backend")
public interface PersistenceBackendProperties
{
    @WithDefault("sqlite")
    BackendMode mode();

    Optional<SQLiteProperties> sqlite();

    Optional<PostgreSQLProperties> postgresql();

    interface SQLiteProperties
    {
        String jdbcUrl();

        Optional<BackupProperties> backup();
    }

    interface PostgreSQLProperties
    {
        Optional<String> jdbcUrl();

        @WithDefault("wombat")
        Optional<String> username();

        Optional<String> password();
    }
}
