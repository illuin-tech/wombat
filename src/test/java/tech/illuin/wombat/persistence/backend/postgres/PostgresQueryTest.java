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
    private static final String MODEL = POSTGRESQL.text("data", "model");

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
            // Every window here is 1000ms long, so the weighting the queries apply is neutral and the
            // expectations below stay the plain sums and averages; weighting itself is covered by
            // KubernetesMetricRepositoryTest against the sqlite chain.
            stmt.executeUpdate(
                "INSERT INTO server_metrics (instant_ms, window_ms, data, cpu_nanocores, ram_bytes, compacted) VALUES"
                + " (1000, 1000, '{\"type\":\"KUBERNETES_API\",\"cluster\":\"c1\",\"namespace\":\"ns1\",\"pod\":\"p1\",\"container\":\"api\"}', 1.5e9, 2e9, 1),"
                + " (1000, 1000, '{\"type\":\"KUBERNETES_API\",\"cluster\":\"c1\",\"namespace\":\"ns1\",\"pod\":\"p1\",\"container\":\"web\"}', 0.5e9, 1e9, 1),"
                + " (2000, 1000, '{\"type\":\"KUBERNETES_API\",\"cluster\":\"c2\",\"namespace\":\"ns2\",\"pod\":\"p2\",\"container\":\"api\"}', 1.0e9, 1e9, 1),"
                // Windows of differing length, past the instant range every other test filters on.
                + " (4000, 1000, '{\"type\":\"KUBERNETES_API\",\"cluster\":\"c3\",\"namespace\":\"ns3\",\"pod\":\"p3\",\"container\":\"api\"}', 1.0e9, 1e9, 1),"
                + " (5000, 3000, '{\"type\":\"KUBERNETES_API\",\"cluster\":\"c3\",\"namespace\":\"ns3\",\"pod\":\"p3\",\"container\":\"api\"}', 3.0e9, 1e9, 1),"
                // Sampling rows, in a cluster of their own so only the tests about them see them.
                + " (9000, 1000, '{\"type\":\"KUBERNETES_API\",\"cluster\":\"c9\",\"namespace\":\"ns9\",\"pod\":\"p9\",\"container\":\"api\"}', 7.0e9, 1e9, 0),"
                + " (3700000, 1000, '{\"type\":\"KUBERNETES_API\",\"cluster\":\"c9\",\"namespace\":\"ns9\",\"pod\":\"p9\",\"container\":\"api\"}', 7.0e9, 1e9, 0)");
            stmt.executeUpdate(
                "INSERT INTO model_metrics (instant_ms, data, output_tokens, compacted) VALUES"
                + " (1000, '{\"profileId\":\"a1\",\"model\":\"m\"}', 100, 1),"
                + " (2000, '{\"profileId\":\"a1\",\"model\":\"m\"}', 200, 1),"
                + " (1500, '{\"profileId\":\"b2\",\"model\":\"m\"}', 999, 1),"
                + " (2500, '{\"profileId\":\"a1\",\"model\":\"m\"}', 50, 0),"
                // One profile serving two models, for the query that narrows to some of them.
                + " (1000, '{\"profileId\":\"d4\",\"model\":\"mistral\"}', 10, 1),"
                + " (1000, '{\"profileId\":\"d4\",\"model\":\"gpt\"}', 40, 1)");
        }
    }

    @Test
    void findByRangeAndClusters_filtersOnTheJsonClusterLabel() throws SQLException
    {
        String sql = "SELECT count(*) FROM server_metrics WHERE instant_ms >= 0 AND instant_ms <= 3000"
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
            + " SUM(cpu_nanocores * window_ms) * 1.0 / NULLIF(SUM(SUM(cpu_nanocores * window_ms)) OVER (), 0) AS share FROM server_metrics"
            + " WHERE compacted = 1 AND instant_ms >= 0 AND instant_ms <= 3000 AND " + CLUSTER + " IN ('c1')"
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
            + " SUM(cpu_nanocores * window_ms) * 1.0 / NULLIF(SUM(SUM(cpu_nanocores * window_ms)) OVER (), 0) AS share FROM server_metrics"
            + " WHERE compacted = 1 AND instant_ms >= 0 AND instant_ms <= 3000"
            + " GROUP BY " + CONTAINER, 2);

        double total = rows.stream().mapToDouble(row -> Double.parseDouble(row[1])).sum();
        assertEquals(1.0, total, 1e-9);
    }

    /**
     * The rows above all cover the same span, which makes the weighting invisible. These two do not:
     * a 1s window at 1.0e9 and a 3s one at 3.0e9 average to 2.0e9 if instants count as equals, and
     * to 2.5e9 once each is weighted by the time it stands for.
     */
    @Test
    void averageCpuPerInstant_weighsEachInstantByItsWindowLength() throws SQLException
    {
        String sql = "SELECT SUM(cpuPerInstant * spanMs) / NULLIF(SUM(spanMs), 0) FROM ("
            + " SELECT SUM(cpu_nanocores) AS cpuPerInstant, MAX(window_ms) AS spanMs FROM server_metrics"
            + " WHERE compacted = 1 AND instant_ms >= 4000 AND instant_ms <= 6000"
            + " GROUP BY instant_ms) AS per_instant";

        assertEquals(2.5e9, scalar(sql).doubleValue(), 1.0);
    }

    /**
     * Per-bucket reporting truncates the instant with the same expression in SELECT and GROUP BY;
     * this pins that Postgres accepts it. A 2s step exercises the grouping over the seeded instants
     * — the hour the UI actually charts is the same expression with a larger step.
     */
    @Test
    void cpuTimePerBucket_groupsRowsByTruncatedInstant() throws SQLException
    {
        String bucket = "instant_ms - (instant_ms % 2000)";
        List<String[]> rows = rows(
            "SELECT " + bucket + " AS bucket, SUM(cpu_nanocores * window_ms) AS cpuTime FROM server_metrics"
            + " WHERE instant_ms >= 0 AND instant_ms <= 6000"
            + " GROUP BY " + bucket + " ORDER BY " + bucket, 2);

        assertEquals(3, rows.size());
        assertEquals("0", rows.get(0)[0]);
        assertEquals(2.0e12, Double.parseDouble(rows.get(0)[1]), 1.0e6);
        assertEquals("2000", rows.get(1)[0]);
        assertEquals(1.0e12, Double.parseDouble(rows.get(1)[1]), 1.0e6);
        // The 3s window at 5000 weighs three times the 1s one at 4000.
        assertEquals("4000", rows.get(2)[0]);
        assertEquals(1.0e13, Double.parseDouble(rows.get(2)[1]), 1.0e6);
    }

    @Test
    void outputTokensPerBucket_sumsTheTokensOfEachBucket() throws SQLException
    {
        String bucket = "instant_ms - (instant_ms % 2000)";
        List<String[]> rows = rows(
            "SELECT " + bucket + " AS bucket, SUM(output_tokens) AS tokens FROM model_metrics"
            + " WHERE compacted = 1 AND instant_ms >= 0 AND instant_ms <= 3000 AND " + PROFILE_ID + " = 'a1'"
            + " GROUP BY " + bucket + " ORDER BY " + bucket, 2);

        assertEquals(2, rows.size());
        assertArrayEqualsAsList(new String[] {"0", "100"}, rows.get(0));
        assertArrayEqualsAsList(new String[] {"2000", "200"}, rows.get(1));
    }

    /**
     * The histogram narrowing an asset to some of its models: the filter is over the {@code model}
     * key, which on Postgres only exists past the jsonb cast the dialect renders.
     */
    @Test
    void outputTokensPerBucket_filtersOnTheJsonModelLabel() throws SQLException
    {
        String bucket = "instant_ms - (instant_ms % 2000)";
        String sql = "SELECT " + bucket + " AS bucket, SUM(output_tokens) AS tokens FROM model_metrics"
            + " WHERE compacted = 1 AND instant_ms >= 0 AND instant_ms <= 3000 AND " + PROFILE_ID + " = 'd4'";

        List<String[]> filtered = rows(sql + " AND " + MODEL + " IN ('mistral')"
            + " GROUP BY " + bucket + " ORDER BY " + bucket, 2);
        assertEquals(1, filtered.size());
        assertArrayEqualsAsList(new String[] {"0", "10"}, filtered.get(0));

        // Unfiltered, the same bucket carries both models — which is what the filter has to remove.
        List<String[]> all = rows(sql + " GROUP BY " + bucket + " ORDER BY " + bucket, 2);
        assertArrayEqualsAsList(new String[] {"0", "50"}, all.get(0));
    }

    /** Rows still being collected are invisible to every serving query, whatever else they match. */
    @Test
    void servingQueries_readFoldedRowsOnly() throws SQLException
    {
        String uncompacted = "SELECT count(*) FROM server_metrics WHERE " + CLUSTER + " IN ('c9')";
        assertEquals(2L, scalar(uncompacted).longValue(), "the fixture holds sampling rows");

        assertEquals(0L, scalar("SELECT count(*) FROM server_metrics"
            + " WHERE compacted = 1 AND " + CLUSTER + " IN ('c9')").longValue());
        assertEquals(300L, scalar(sumOutputTokens("a1", 0, 3000)).longValue(), "the 50-token sampling row is left out");
    }

    /** The window listing compaction works from: sampling rows only, one entry per window, ordered. */
    @Test
    void uncompactedBuckets_listsTheWindowsHoldingSamplingRows() throws SQLException
    {
        String bucket = "instant_ms - (instant_ms % 3600000)";
        List<String[]> rows = rows(
            "SELECT DISTINCT " + bucket + " AS bucket FROM server_metrics"
            + " WHERE compacted = 0 AND instant_ms < 7200000 ORDER BY " + bucket, 1);

        assertEquals(2, rows.size());
        assertEquals("0", rows.get(0)[0]);
        assertEquals("3600000", rows.get(1)[0]);
    }

    @Test
    void containerLocations_areDistinctAndOrdered() throws SQLException
    {
        List<String[]> rows = rows(
            "SELECT DISTINCT " + CONTAINER + " AS container,"
            + " " + CLUSTER + " AS cluster,"
            + " " + NAMESPACE + " AS namespace FROM server_metrics"
            + " WHERE compacted = 1 AND instant_ms >= 0 AND instant_ms <= 3000 AND " + CLUSTER + " IN ('c1', 'c2')"
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
        return "SELECT SUM(cpuPerInstant * spanMs) / NULLIF(SUM(spanMs), 0) FROM ("
            + " SELECT SUM(cpu_nanocores) AS cpuPerInstant, MAX(window_ms) AS spanMs FROM server_metrics"
            + " WHERE compacted = 1 AND instant_ms >= 0 AND instant_ms <= 3000" + clusterFilter
            + " GROUP BY instant_ms) AS per_instant";
    }

    private static String sumOutputTokens(String profileId, long startMs, long endMs)
    {
        return "SELECT COALESCE(SUM(output_tokens), 0) FROM model_metrics"
            + " WHERE compacted = 1 AND instant_ms >= " + startMs + " AND instant_ms <= " + endMs
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
