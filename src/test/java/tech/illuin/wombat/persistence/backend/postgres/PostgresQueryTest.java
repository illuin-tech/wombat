package tech.illuin.wombat.persistence.backend.postgres;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.illuin.wombat.persistence.dialect.JsonPathDialect.POSTGRESQL;

/**
 * Runs the queries of KubernetesMetricRepository and LLMModelMetricRepository, in the form the
 * POSTGRESQL dialect renders them, against a real server. The query skeletons are mirrored here
 * rather than driven through the repositories themselves, which need the CDI container: keep the two
 * in step, and see the note in the class javadoc of the repositories when changing either.
 */
class PostgresQueryTest
{
    private static final String CLUSTER = POSTGRESQL.text("data", "cluster");
    private static final String CONTAINER = POSTGRESQL.text("data", "container");
    private static final String NAMESPACE = POSTGRESQL.text("data", "namespace");
    private static final String PROFILE_ID = POSTGRESQL.text("data", "profileId");

    private static String jdbcUrl;

    @BeforeAll
    static void seed() throws SQLException
    {
        jdbcUrl = PostgresTestContainer.freshDatabase("wombat_query");
        Flyway.configure()
            .dataSource(jdbcUrl, PostgresTestContainer.username(), PostgresTestContainer.password())
            .locations("classpath:db/migration/postgresql")
            .load()
            .migrate();

        try (Connection conn = PostgresTestContainer.connect(jdbcUrl);
             Statement stmt = conn.createStatement())
        {
            stmt.executeUpdate(
                "INSERT INTO server_metrics (instantMs, data, cpu_nanocores, ram_bytes) VALUES"
                + " (1000, '{\"type\":\"KUBERNETES_API\",\"cluster\":\"c1\",\"namespace\":\"ns1\",\"pod\":\"p1\",\"container\":\"api\"}', 1.5e9, 2e9),"
                + " (1000, '{\"type\":\"KUBERNETES_API\",\"cluster\":\"c1\",\"namespace\":\"ns1\",\"pod\":\"p1\",\"container\":\"web\"}', 0.5e9, 1e9),"
                + " (2000, '{\"type\":\"KUBERNETES_API\",\"cluster\":\"c2\",\"namespace\":\"ns2\",\"pod\":\"p2\",\"container\":\"api\"}', 1.0e9, 1e9)");
            stmt.executeUpdate(
                "INSERT INTO model_metrics (instantMs, data, outputTokens) VALUES"
                + " (1000, '{\"profileId\":\"a1\",\"model\":\"m\"}', 100),"
                + " (2000, '{\"profileId\":\"a1\",\"model\":\"m\"}', 200),"
                + " (1500, '{\"profileId\":\"b2\",\"model\":\"m\"}', 999)");
        }
    }

    @Test
    void findByRangeAndClusters_filtersOnTheJsonClusterLabel() throws SQLException
    {
        String sql = "SELECT count(*) FROM server_metrics WHERE instantMs >= 0 AND instantMs <= 3000"
            + " AND " + CLUSTER + " IN ('c1')";

        assertEquals(2L, scalar(sql).longValue());
    }

    @Test
    void averageCpuPerInstant_averagesThePerInstantSums() throws SQLException
    {
        // t=1000 sums to 2.0e9 (1.5 + 0.5), t=2000 to 1.0e9, so the average per instant is 1.5e9.
        assertEquals(1.5e9, scalar(averageCpuPerInstant("")).doubleValue(), 1.0);
    }

    @Test
    void averageCpuPerInstant_appliesTheClusterFilterBeforeAveraging() throws SQLException
    {
        assertEquals(2.0e9, scalar(averageCpuPerInstant(" AND " + CLUSTER + " IN ('c1')")).doubleValue(), 1.0);
    }

    @Test
    void containerShares_areTheCpuFractionOfEachContainer() throws SQLException
    {
        List<String[]> rows = rows(
            "SELECT " + CONTAINER + " AS container,"
            + " SUM(cpu_nanocores) * 1.0 / SUM(SUM(cpu_nanocores)) OVER () AS share FROM server_metrics"
            + " WHERE instantMs >= 0 AND instantMs <= 3000 AND " + CLUSTER + " IN ('c1')"
            + " GROUP BY " + CONTAINER, 2);

        assertEquals(2, rows.size());
        assertEquals("api", rows.get(0)[0]);
        assertEquals(0.75, Double.parseDouble(rows.get(0)[1]), 1e-9);
        assertEquals("web", rows.get(1)[0]);
        assertEquals(0.25, Double.parseDouble(rows.get(1)[1]), 1e-9);
    }

    @Test
    void containerShares_sumToOneAcrossAllClusters() throws SQLException
    {
        List<String[]> rows = rows(
            "SELECT " + CONTAINER + " AS container,"
            + " SUM(cpu_nanocores) * 1.0 / SUM(SUM(cpu_nanocores)) OVER () AS share FROM server_metrics"
            + " WHERE instantMs >= 0 AND instantMs <= 3000"
            + " GROUP BY " + CONTAINER, 2);

        double total = rows.stream().mapToDouble(row -> Double.parseDouble(row[1])).sum();
        assertEquals(1.0, total, 1e-9);
    }

    @Test
    void containerLocations_areDistinctAndOrdered() throws SQLException
    {
        List<String[]> rows = rows(
            "SELECT DISTINCT " + CONTAINER + " AS container,"
            + " " + CLUSTER + " AS cluster,"
            + " " + NAMESPACE + " AS namespace FROM server_metrics"
            + " WHERE instantMs >= 0 AND instantMs <= 3000 AND " + CLUSTER + " IN ('c1', 'c2')"
            + " ORDER BY container, cluster, namespace", 3);

        assertEquals(3, rows.size());
        assertArrayEqualsAsList(new String[] {"api", "c1", "ns1"}, rows.get(0));
        assertArrayEqualsAsList(new String[] {"api", "c2", "ns2"}, rows.get(1));
        assertArrayEqualsAsList(new String[] {"web", "c1", "ns1"}, rows.get(2));
    }

    @Test
    void sumOutputTokens_addsUpOnlyTheRequestedProfile() throws SQLException
    {
        assertEquals(300L, scalar(sumOutputTokens("a1", 0, 3000)).longValue());
        assertEquals(999L, scalar(sumOutputTokens("b2", 0, 3000)).longValue());
    }

    @Test
    void sumOutputTokens_honoursTheInstantRange() throws SQLException
    {
        assertEquals(100L, scalar(sumOutputTokens("a1", 0, 1500)).longValue());
    }

    @Test
    void sumOutputTokens_returnsZeroRatherThanNullWhenNothingMatches() throws SQLException
    {
        assertEquals(0L, scalar(sumOutputTokens("unknown", 0, 3000)).longValue());
    }

    @Test
    void sizeGauge_reportsThePositiveSizeOfTheDatabase()
    {
        double size = new PostgresSizeGauge(PostgresTestContainer.dataSource(jdbcUrl)).sizeBytes();

        assertTrue(size > 0, "expected a positive database size, got " + size);
    }

    /**
     * The derived table carries an explicit alias: Postgres 15 and earlier reject a subquery in FROM
     * without one, so dropping it would break the query on those versions only.
     */
    private static String averageCpuPerInstant(String clusterFilter)
    {
        return "SELECT AVG(perInstant) FROM ("
            + " SELECT SUM(cpu_nanocores) AS perInstant FROM server_metrics"
            + " WHERE instantMs >= 0 AND instantMs <= 3000" + clusterFilter
            + " GROUP BY instantMs) AS per_instant";
    }

    private static String sumOutputTokens(String profileId, long startMs, long endMs)
    {
        return "SELECT COALESCE(SUM(outputTokens), 0) FROM model_metrics"
            + " WHERE instantMs >= " + startMs + " AND instantMs <= " + endMs
            + " AND " + PROFILE_ID + " = '" + profileId + "'";
    }

    private static Number scalar(String sql) throws SQLException
    {
        try (Connection conn = PostgresTestContainer.connect(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql))
        {
            rs.next();
            return (Number) rs.getObject(1);
        }
    }

    private static List<String[]> rows(String sql, int columns) throws SQLException
    {
        List<String[]> results = new ArrayList<>();
        try (Connection conn = PostgresTestContainer.connect(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql))
        {
            while (rs.next())
            {
                String[] row = new String[columns];
                for (int i = 0; i < columns; i++)
                    row[i] = rs.getString(i + 1);
                results.add(row);
            }
        }
        return results;
    }

    private static void assertArrayEqualsAsList(String[] expected, String[] actual)
    {
        assertEquals(List.of(expected), List.of(actual));
    }
}
