package tech.illuin.wombat.persistence;

import tech.illuin.wombat.persistence.PersistenceBackendProperties.PostgreSQLProperties;
import tech.illuin.wombat.persistence.PersistenceBackendProperties.SQLiteProperties;

import java.util.Map;

public enum BackendMode
{
    sqlite(Map.of(
        "persistence.backend.sqlite.jdbc-url", "quarkus.datasource.sqlite.jdbc.url"
    ))
    {
        @Override
        public SQLiteProperties getSection(PersistenceBackendProperties parent)
        {
            return parent.sqlite().orElseThrow(() -> new IllegalArgumentException("sqlite properties required when mode=sqlite"));
        }
    },
    postgresql(Map.of(
        "persistence.backend.postgresql.jdbc-url", "quarkus.datasource.postgresql.jdbc.url",
        "persistence.backend.postgresql.username", "quarkus.datasource.postgresql.username",
        "persistence.backend.postgresql.password", "quarkus.datasource.postgresql.password"
    ))
    {
        @Override
        public PostgreSQLProperties getSection(PersistenceBackendProperties parent)
        {
            return parent.postgresql().orElseThrow(() -> new IllegalArgumentException("postgresql properties required when mode=postgresql"));
        }
    };

    private final Map<String, String> configMap;

    BackendMode(Map<String, String> configMap)
    {
        this.configMap = configMap;
    }

    public abstract <T> T getSection(PersistenceBackendProperties parent);

    public Map<String, String> configMap()
    {
        return this.configMap;
    }
}
