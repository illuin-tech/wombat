package tech.illuin.wombat.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.illuin.wombat.persistence.backend.postgres.PostgresTestContainer;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two migration chains are independent — sqlite keeps the incremental V1..V10 history that
 * existing local databases replay, postgresql a single baseline — but they must land on the same
 * structure. This replays both against real databases and compares what they produced, so a change
 * applied to one chain and forgotten in the other fails here rather than at runtime.
 * <p>
 * Only the structure is compared, and only the parts that are meaningfully comparable: SQLite has no
 * timestamp type, no identity columns and no expression indexes, so exact DDL equality is not the
 * bar. See {@link #TYPE_EQUIVALENCES} for the type mapping the comparison accepts.
 */
class SchemaParityTest
{
    /**
     * The intended SQLite-to-Postgres column type mapping. INTEGER admits two Postgres types: the
     * sqlite-jdbc driver stores java.time.Instant as epoch millis in an INTEGER column, where
     * Postgres uses a real timestamp (asserted precisely in PostgresSchemaTest).
     */
    private static final Map<String, Set<String>> TYPE_EQUIVALENCES = Map.of(
        "TEXT", Set.of("text"),
        "REAL", Set.of("double precision"),
        "INTEGER", Set.of("bigint", "timestamp with time zone")
    );

    private static final String FLYWAY_HISTORY = "flyway_schema_history";

    @TempDir
    static Path tempDir;

    private static String sqliteUrl;
    private static String postgresUrl;

    @BeforeAll
    static void migrateBothChains() throws SQLException
    {
        sqliteUrl = "jdbc:sqlite:" + tempDir.resolve("parity.db");
        Flyway.configure()
            .dataSource(sqliteUrl, null, null)
            .locations("classpath:db/migration/sqlite")
            .load()
            .migrate();

        postgresUrl = PostgresTestContainer.freshDatabase("wombat_parity");
        Flyway.configure()
            .dataSource(postgresUrl, PostgresTestContainer.username(), PostgresTestContainer.password())
            .locations("classpath:db/migration/postgresql")
            .load()
            .migrate();
    }

    @Test
    void bothChains_produceTheSameTables() throws SQLException
    {
        Set<String> tables = sqliteTables();

        assertTrue(tables.size() >= 5, "expected the sqlite chain to produce tables, got " + tables);
        assertEquals(tables, postgresTables());
    }

    @Test
    void everyTable_hasTheSameColumns() throws SQLException
    {
        for (String table : sqliteTables())
            assertEquals(sqliteColumns(table).keySet(), postgresColumns(table).keySet(), "columns of " + table);
    }

    @Test
    void everyColumn_hasAnEquivalentType() throws SQLException
    {
        for (String table : sqliteTables())
        {
            Map<String, Column> sqlite = sqliteColumns(table);
            Map<String, Column> postgres = postgresColumns(table);

            for (Map.Entry<String, Column> entry : sqlite.entrySet())
            {
                String sqliteType = entry.getValue().type().toUpperCase(Locale.ROOT);
                String postgresType = postgres.get(entry.getKey()).type();
                Set<String> accepted = TYPE_EQUIVALENCES.get(sqliteType);

                assertTrue(accepted != null, "no equivalence declared for sqlite type " + sqliteType
                    + " (" + table + "." + entry.getKey() + ")");
                assertTrue(accepted.contains(postgresType), table + "." + entry.getKey()
                    + ": sqlite " + sqliteType + " maps to " + accepted + " but postgres has " + postgresType);
            }
        }
    }

    @Test
    void everyColumn_hasTheSameNullability() throws SQLException
    {
        for (String table : sqliteTables())
            assertEquals(nullabilityOf(sqliteColumns(table)), nullabilityOf(postgresColumns(table)), "nullability of " + table);
    }

    @Test
    void everyTable_hasTheSamePrimaryKey() throws SQLException
    {
        for (String table : sqliteTables())
            assertEquals(sqlitePrimaryKey(table), postgresPrimaryKey(table), "primary key of " + table);
    }

    /**
     * Compares the explicitly created indexes by name and uniqueness. The expressions they index
     * cannot be compared — json_extract against a jsonb cast — and the implicit primary-key indexes
     * are named differently by each engine, so both are left out.
     */
    @Test
    void bothChains_declareTheSameIndexes() throws SQLException
    {
        Set<String> indexes = sqliteIndexes();

        assertTrue(indexes.size() >= 5, "expected explicit indexes in the sqlite chain, got " + indexes);
        assertEquals(indexes, postgresIndexes());
    }

    @Test
    void bothChains_declareTheSameForeignKeys() throws SQLException
    {
        Set<String> sqliteKeys = new TreeSet<>();
        Set<String> postgresKeys = new TreeSet<>();
        for (String table : sqliteTables())
        {
            sqliteKeys.addAll(sqliteForeignKeys(table));
            postgresKeys.addAll(postgresForeignKeys(table));
        }

        assertTrue(sqliteKeys.contains("assets.environment_id -> environments.id"), sqliteKeys.toString());
        assertEquals(sqliteKeys, postgresKeys);
    }

    private static Map<String, Boolean> nullabilityOf(Map<String, Column> columns)
    {
        Map<String, Boolean> nullability = new TreeMap<>();
        columns.forEach((name, column) -> nullability.put(name, column.nullable()));
        return nullability;
    }

    private static Set<String> sqliteTables() throws SQLException
    {
        Set<String> tables = new TreeSet<>();
        try (Connection conn = DriverManager.getConnection(sqliteUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'"))
        {
            while (rs.next())
            {
                String name = rs.getString(1).toLowerCase(Locale.ROOT);
                if (!name.startsWith("sqlite_") && !FLYWAY_HISTORY.equals(name))
                    tables.add(name);
            }
        }
        return tables;
    }

    private static Set<String> postgresTables() throws SQLException
    {
        Set<String> tables = new TreeSet<>();
        try (Connection conn = PostgresTestContainer.connect(postgresUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT table_name FROM information_schema.tables"
                 + " WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"))
        {
            while (rs.next())
            {
                String name = rs.getString(1);
                if (!FLYWAY_HISTORY.equals(name))
                    tables.add(name);
            }
        }
        return tables;
    }

    /**
     * SQLite reports a TEXT primary key as nullable, while a Postgres PRIMARY KEY always implies NOT
     * NULL; primary-key columns are therefore normalised to non-nullable on both sides.
     */
    private static Map<String, Column> sqliteColumns(String table) throws SQLException
    {
        Map<String, Column> columns = new LinkedHashMap<>();
        try (Connection conn = DriverManager.getConnection(sqliteUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")"))
        {
            while (rs.next())
            {
                boolean primaryKey = rs.getInt("pk") > 0;
                boolean nullable = rs.getInt("notnull") == 0 && !primaryKey;
                columns.put(rs.getString("name").toLowerCase(Locale.ROOT), new Column(rs.getString("type"), nullable));
            }
        }
        return columns;
    }

    private static Map<String, Column> postgresColumns(String table) throws SQLException
    {
        Map<String, Column> columns = new LinkedHashMap<>();
        try (Connection conn = PostgresTestContainer.connect(postgresUrl);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT column_name, data_type, is_nullable FROM information_schema.columns"
                 + " WHERE table_schema = 'public' AND table_name = ? ORDER BY ordinal_position"))
        {
            stmt.setString(1, table);
            try (ResultSet rs = stmt.executeQuery())
            {
                while (rs.next())
                    columns.put(rs.getString(1), new Column(rs.getString(2), "YES".equals(rs.getString(3))));
            }
        }
        return columns;
    }

    private static Set<String> sqlitePrimaryKey(String table) throws SQLException
    {
        Set<String> key = new TreeSet<>();
        try (Connection conn = DriverManager.getConnection(sqliteUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")"))
        {
            while (rs.next())
            {
                if (rs.getInt("pk") > 0)
                    key.add(rs.getString("name").toLowerCase(Locale.ROOT));
            }
        }
        return key;
    }

    private static Set<String> postgresPrimaryKey(String table) throws SQLException
    {
        Set<String> key = new TreeSet<>();
        try (Connection conn = PostgresTestContainer.connect(postgresUrl);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT a.attname FROM pg_index i"
                 + " JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)"
                 + " WHERE i.indrelid = to_regclass(?) AND i.indisprimary"))
        {
            stmt.setString(1, "public." + table);
            try (ResultSet rs = stmt.executeQuery())
            {
                while (rs.next())
                    key.add(rs.getString(1));
            }
        }
        return key;
    }

    private static Set<String> sqliteIndexes() throws SQLException
    {
        Set<String> indexes = new TreeSet<>();
        for (String table : sqliteTables())
        {
            try (Connection conn = DriverManager.getConnection(sqliteUrl);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA index_list(" + table + ")"))
            {
                while (rs.next())
                {
                    // origin 'c' is an index we created; 'pk' and 'u' are implicit constraint indexes.
                    if ("c".equals(rs.getString("origin")))
                        indexes.add(describeIndex(table, rs.getString("name"), rs.getInt("unique") == 1));
                }
            }
        }
        return indexes;
    }

    private static Set<String> postgresIndexes() throws SQLException
    {
        Set<String> indexes = new TreeSet<>();
        try (Connection conn = PostgresTestContainer.connect(postgresUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT tablename, indexname, indexdef FROM pg_indexes WHERE schemaname = 'public'"))
        {
            while (rs.next())
            {
                String name = rs.getString("indexname");
                if (name.endsWith("_pkey") || FLYWAY_HISTORY.equals(rs.getString("tablename")))
                    continue;
                indexes.add(describeIndex(rs.getString("tablename"), name, rs.getString("indexdef").contains("CREATE UNIQUE INDEX")));
            }
        }
        return indexes;
    }

    private static String describeIndex(String table, String name, boolean unique)
    {
        return table + "." + name.toLowerCase(Locale.ROOT) + (unique ? " (unique)" : "");
    }

    private static Set<String> sqliteForeignKeys(String table) throws SQLException
    {
        Set<String> keys = new TreeSet<>();
        try (Connection conn = DriverManager.getConnection(sqliteUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_list(" + table + ")"))
        {
            while (rs.next())
                keys.add(describeForeignKey(table, rs.getString("from"), rs.getString("table"), rs.getString("to")));
        }
        return keys;
    }

    private static Set<String> postgresForeignKeys(String table) throws SQLException
    {
        Set<String> keys = new TreeSet<>();
        try (Connection conn = PostgresTestContainer.connect(postgresUrl);
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT kcu.column_name, ccu.table_name, ccu.column_name FROM information_schema.table_constraints tc"
                 + " JOIN information_schema.key_column_usage kcu ON kcu.constraint_name = tc.constraint_name"
                 + " JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name"
                 + " WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public' AND tc.table_name = ?"))
        {
            stmt.setString(1, table);
            try (ResultSet rs = stmt.executeQuery())
            {
                while (rs.next())
                    keys.add(describeForeignKey(table, rs.getString(1), rs.getString(2), rs.getString(3)));
            }
        }
        return keys;
    }

    private static String describeForeignKey(String table, String column, String targetTable, String targetColumn)
    {
        return table + "." + column.toLowerCase(Locale.ROOT)
            + " -> " + targetTable.toLowerCase(Locale.ROOT) + "." + targetColumn.toLowerCase(Locale.ROOT);
    }

    private record Column(String type, boolean nullable) {}
}
