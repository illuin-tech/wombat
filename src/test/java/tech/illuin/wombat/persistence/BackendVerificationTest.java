package tech.illuin.wombat.persistence;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.persistence.backend.PersistenceEngine;
import tech.illuin.wombat.persistence.dialect.JsonPathDialect;

import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies that the SQLite file backend is mounted when configured with a file-based JDBC URL. */
@QuarkusTest
@TestProfile(SQLiteFileBackendVerificationTest.SQLiteFileProfile.class)
class SQLiteFileBackendVerificationTest
{
    @Inject PersistenceEngine engine;
    @Inject PersistenceBackendProperties backendProps;
    @Inject JsonPathDialect jsonPathDialect;

    @Test
    void sqlite_file_backend_is_mounted()
    {
        assertTrue(this.engine.isMounted("sqlite"), "SQLite backend should be mounted");
        assertEquals(BackendMode.sqlite, this.backendProps.mode(), "Backend mode should be sqlite");
        assertSame(JsonPathDialect.SQLITE, this.jsonPathDialect, "JsonPathDialect should be SQLITE");
    }

    public static class SQLiteFileProfile implements QuarkusTestProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            return Map.of(
                "persistence.backend.mode", "sqlite",
                "persistence.backend.sqlite.jdbc-url", "jdbc:sqlite:target/wombat-verify-file.db"
            );
        }
    }
}

/** Verifies that the SQLite in-memory backend is mounted when configured with jdbc:sqlite::memory:. */
@QuarkusTest
@TestProfile(SQLiteInMemoryBackendVerificationTest.SQLiteInMemoryProfile.class)
class SQLiteInMemoryBackendVerificationTest
{
    @Inject PersistenceEngine engine;
    @Inject PersistenceBackendProperties backendProps;
    @Inject JsonPathDialect jsonPathDialect;

    @Test
    void sqlite_in_memory_backend_is_mounted()
    {
        assertTrue(this.engine.isMounted("sqlite"), "SQLite backend should be mounted");
        assertEquals(BackendMode.sqlite, this.backendProps.mode(), "Backend mode should be sqlite");
        assertSame(JsonPathDialect.SQLITE, this.jsonPathDialect, "JsonPathDialect should be SQLITE");
    }

    public static class SQLiteInMemoryProfile implements QuarkusTestProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            return Map.of(
                "persistence.backend.mode", "sqlite",
                "persistence.backend.sqlite.jdbc-url", "jdbc:sqlite::memory:"
            );
        }
    }
}

/** Verifies that the PostgreSQL backend is mounted when configured with mode=postgresql. */
@QuarkusTest
@TestProfile(PostgresBackendVerificationTest.PostgresProfile.class)
class PostgresBackendVerificationTest
{
    @Inject PersistenceEngine engine;
    @Inject PersistenceBackendProperties backendProps;
    @Inject JsonPathDialect jsonPathDialect;

    @Test
    void postgres_backend_is_mounted()
    {
        assertTrue(this.engine.isMounted("postgresql"), "PostgreSQL backend should be mounted");
        assertEquals(BackendMode.postgresql, this.backendProps.mode(), "Backend mode should be postgresql");
        assertSame(JsonPathDialect.POSTGRESQL, this.jsonPathDialect, "JsonPathDialect should be POSTGRESQL");
    }

    public static class PostgresProfile implements QuarkusTestProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            String jdbcUrl;
            try {
                jdbcUrl = tech.illuin.wombat.persistence.backend.postgres.PostgresTestContainer.freshDatabase("wombat_backend_verify");
            }
            catch (SQLException e) {
                throw new IllegalStateException("Could not create PostgreSQL test database", e);
            }
            return Map.ofEntries(
                Map.entry("persistence.backend.mode", "postgresql"),
                Map.entry("persistence.backend.postgresql.jdbc-url", jdbcUrl),
                Map.entry("persistence.backend.postgresql.username", tech.illuin.wombat.persistence.backend.postgres.PostgresTestContainer.username()),
                Map.entry("persistence.backend.postgresql.password", tech.illuin.wombat.persistence.backend.postgres.PostgresTestContainer.password()),
                Map.entry("quarkus.datasource.db-kind", "postgresql"),
                Map.entry("quarkus.datasource.username", tech.illuin.wombat.persistence.backend.postgres.PostgresTestContainer.username()),
                Map.entry("quarkus.datasource.password", tech.illuin.wombat.persistence.backend.postgres.PostgresTestContainer.password()),
                Map.entry("quarkus.datasource.jdbc.url", jdbcUrl),
                Map.entry("quarkus.flyway.locations", "classpath:db/migration/postgresql"),
                Map.entry("quarkus.flyway.migrate-at-start", "true"),
                Map.entry("quarkus.flyway.clean-at-start", "true"),
                Map.entry("quarkus.flyway.clean-disabled", "false")
            );
        }
    }
}

/** Verifies that aggregator mode keeps the configured backend (not overridden). */
@QuarkusTest
@TestProfile(AggregatorBackendVerificationTest.AggregatorProfile.class)
class AggregatorBackendVerificationTest
{
    @Inject PersistenceEngine engine;
    @Inject PersistenceBackendProperties backendProps;
    @Inject JsonPathDialect jsonPathDialect;

    @Test
    void aggregator_keeps_configured_backend()
    {
        assertTrue(this.engine.isMounted("sqlite"), "Aggregator should mount sqlite backend");
        assertEquals(BackendMode.sqlite, this.backendProps.mode(), "Aggregator mode should keep sqlite");
        assertSame(JsonPathDialect.SQLITE, this.jsonPathDialect, "Aggregator should use SQLITE dialect");
    }

    public static class AggregatorProfile implements QuarkusTestProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            return Map.of(
                "wombat.mode", "aggregator",
                "persistence.backend.mode", "sqlite",
                "persistence.backend.sqlite.jdbc-url", "jdbc:sqlite::memory:"
            );
        }
    }
}

/** Verifies that worker mode forces sqlite in-memory via NodeModeConfigSourceFactory. */
@QuarkusTest
@TestProfile(WorkerBackendVerificationTest.WorkerProfile.class)
class WorkerBackendVerificationTest
{
    @Inject PersistenceEngine engine;
    @Inject PersistenceBackendProperties backendProps;
    @Inject JsonPathDialect jsonPathDialect;

    @Test
    void worker_forces_sqlite_in_memory_override()
    {
        assertTrue(this.engine.isMounted("sqlite"), "Worker should mount sqlite backend (override)");
        assertEquals(BackendMode.sqlite, this.backendProps.mode(), "Worker mode should override to sqlite");
        assertSame(JsonPathDialect.SQLITE, this.jsonPathDialect, "Worker should use SQLITE dialect");
    }

    public static class WorkerProfile implements QuarkusTestProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            return Map.of(
                "wombat.mode", "worker",
                "wombat.worker.id", "test-worker",
                "wombat.worker.aggregator.url", "http://localhost:0",
                "wombat.worker.aggregator.api-key", "unused",
                "persistence.backend.mode", "sqlite",
                "persistence.backend.sqlite.jdbc-url", "jdbc:sqlite::memory:"
            );
        }
    }
}
