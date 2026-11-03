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
                "INSERT INTO server_metrics (instant_ms, window_ms, asset_id, environment_id, asset_name, asset_type, data, cpu_nanocores, ram_bytes, compacted) VALUES"
                + " (1000, 1000, 'c1', 'env', 'Cluster One', 'tech.illuin.wombat-module.kubernetes-api', '{\"cluster\":\"c1\",\"namespace\":\"ns1\",\"pod\":\"p1\",\"container\":\"api\"}', 1.5e9, 2e9, 1),"
                + " (1000, 1000, 'c1', 'env', 'Cluster One', 'tech.illuin.wombat-module.kubernetes-api', '{\"cluster\":\"c1\",\"namespace\":\"ns1\",\"pod\":\"p1\",\"container\":\"web\"}', 0.5e9, 1e9, 1),"
                + " (2000, 1000, 'c2', 'env', 'Cluster Two', 'tech.illuin.wombat-module.kubernetes-api', '{\"cluster\":\"c2\",\"namespace\":\"ns2\",\"pod\":\"p2\",\"container\":\"api\"}', 1.0e9, 1e9, 1),"
                // Windows of differing length, past the instant range every other test filters on.
                + " (4000, 1000, 'c3', 'env', 'Cluster Three', 'tech.illuin.wombat-module.kubernetes-api', '{\"cluster\":\"c3\",\"namespace\":\"ns3\",\"pod\":\"p3\",\"container\":\"api\"}', 1.0e9, 1e9, 1),"
                + " (5000, 3000, 'c3', 'env', 'Cluster Three', 'tech.illuin.wombat-module.kubernetes-api', '{\"cluster\":\"c3\",\"namespace\":\"ns3\",\"pod\":\"p3\",\"container\":\"api\"}', 3.0e9, 1e9, 1),"
                // Sampling rows, under an asset of their own so only the tests about them see them.
                + " (9000, 1000, 'c9', 'env', 'Cluster Nine', 'tech.illuin.wombat-module.kubernetes-api', '{\"cluster\":\"c9\",\"namespace\":\"ns9\",\"pod\":\"p9\",\"container\":\"api\"}', 7.0e9, 1e9, 0),"
                + " (3700000, 1000, 'c9', 'env', 'Cluster Nine', 'tech.illuin.wombat-module.kubernetes-api', '{\"cluster\":\"c9\",\"namespace\":\"ns9\",\"pod\":\"p9\",\"container\":\"api\"}', 7.0e9, 1e9, 0)");
            stmt.executeUpdate(
                "INSERT INTO model_metrics (instant_ms, asset_id, environment_id, asset_name, asset_type, data, output_tokens, compacted) VALUES"
                + " (1000, 'a1', 'env', 'Asset One', 'tech.illuin.wombat-module.llm-prometheus', '{\"model\":\"m\"}', 100, 1),"
                + " (2000, 'a1', 'env', 'Asset One', 'tech.illuin.wombat-module.llm-prometheus', '{\"model\":\"m\"}', 200, 1),"
                + " (1500, 'b2', 'env', 'Asset Two', 'tech.illuin.wombat-module.llm-prometheus', '{\"model\":\"m\"}', 999, 1),"
                + " (2500, 'a1', 'env', 'Asset One', 'tech.illuin.wombat-module.llm-prometheus', '{\"model\":\"m\"}', 50, 0)");
        }
    }

    @Test
    void findByRangeAndAssets_filtersOnTheAssetColumn() throws SQLException
    {
        String sql = "SELECT count(*) FROM server_metrics WHERE instant_ms >= 0 AND instant_ms <= 3000"
            + " AND asset_id IN ('c1')";

        assertEquals(2L, scalar(sql).longValue());
    }

    @Test
    void averageCpuPerBucket_averagesThePerInstantSums() throws SQLException
    {
        // t=1000 sums to 2.0e9 (1.5 + 0.5), t=2000 to 1.0e9, so the average per instant is 1.5e9.
        List<String[]> rows = rows(averageCpuPerBucket(0, 3000, 3000, ""), 2);

        assertEquals(1, rows.size());
        assertEquals("0", rows.get(0)[0]);
        assertEquals(1.5e9, Double.parseDouble(rows.get(0)[1]), 1.0);
    }

    @Test
    void averageCpuPerBucket_appliesTheAssetFilterBeforeAveraging() throws SQLException
    {
        List<String[]> rows = rows(averageCpuPerBucket(0, 3000, 3000, " AND asset_id IN ('c1')"), 2);

        assertEquals(2.0e9, Double.parseDouble(rows.get(0)[1]), 1.0);
    }

    /**
     * Buckets are laid from the start of the requested range, as the slots of the evaluation are,
     * not from the epoch: starting at 500 with a 1s step puts t=1000 in the 500 bucket.
     */
    @Test
    void averageCpuPerBucket_groupsInstantsIntoBucketsAlignedOnTheStart() throws SQLException
    {
        List<String[]> rows = rows(averageCpuPerBucket(500, 2500, 1000, ""), 2);

        assertEquals(2, rows.size());
        assertEquals("500", rows.get(0)[0]);
        assertEquals(2.0e9, Double.parseDouble(rows.get(0)[1]), 1.0);
        assertEquals("1500", rows.get(1)[0]);
        assertEquals(1.0e9, Double.parseDouble(rows.get(1)[1]), 1.0);
    }

    /**
     * The rows above all cover the same span, which makes the weighting invisible. These two do not:
     * a 1s window at 1.0e9 and a 3s one at 3.0e9 average to 2.0e9 if instants count as equals, and
     * to 2.5e9 once each is weighted by the time it stands for.
     */
    @Test
    void averageCpuPerBucket_weighsEachInstantByItsWindowLength() throws SQLException
    {
        List<String[]> rows = rows(averageCpuPerBucket(4000, 8000, 4000, ""), 2);

        assertEquals(1, rows.size());
        assertEquals(2.5e9, Double.parseDouble(rows.get(0)[1]), 1.0);
    }

    @Test
    void containerSharesPerBucket_areTheCpuFractionOfEachContainer() throws SQLException
    {
        List<String[]> rows = rows(containerSharesPerBucket(0, 3000, 3000, " AND asset_id IN ('c1')"), 3);

        assertEquals(2, rows.size());
        assertArrayEqualsAsList(new String[] {"0", "api"}, new String[] {rows.get(0)[0], rows.get(0)[1]});
        assertEquals(0.75, Double.parseDouble(rows.get(0)[2]), 1e-9);
        assertArrayEqualsAsList(new String[] {"0", "web"}, new String[] {rows.get(1)[0], rows.get(1)[1]});
        assertEquals(0.25, Double.parseDouble(rows.get(1)[2]), 1e-9);
    }

    /** The window function is partitioned by bucket, so the shares sum to one within each bucket. */
    @Test
    void containerSharesPerBucket_sumToOneWithinEachBucket() throws SQLException
    {
        List<String[]> rows = rows(containerSharesPerBucket(1000, 3000, 1000, ""), 3);

        assertEquals(3, rows.size());
        assertEquals(1.0, Double.parseDouble(rows.get(0)[2]) + Double.parseDouble(rows.get(1)[2]), 1e-9);
        assertArrayEqualsAsList(new String[] {"2000", "api"}, new String[] {rows.get(2)[0], rows.get(2)[1]});
        assertEquals(1.0, Double.parseDouble(rows.get(2)[2]), 1e-9);
    }

    /**
     * Per-slot LLM activity groups on the start-aligned bucket and on the JSON labels together, which
     * on Postgres are only reachable past the jsonb cast the dialect renders.
     */
    @Test
    void serviceActivitiesPerBucket_groupsTheLabelsOfEachBucket() throws SQLException
    {
        String bucket = bucket(0, 2000);
        String labels = POSTGRESQL.text("data", "serviceId") + ", " + POSTGRESQL.text("data", "provider")
            + ", " + POSTGRESQL.text("data", "model") + ", " + POSTGRESQL.text("data", "location");
        List<String[]> rows = rows(
            "SELECT " + bucket + " AS bucket, " + labels + ", SUM(output_tokens) AS tokens, SUM(request_count) AS requests"
            + " FROM model_metrics WHERE compacted = 1 AND instant_ms >= 0 AND instant_ms < 3000 AND asset_id = 'a1'"
            + " GROUP BY " + bucket + ", " + labels + " ORDER BY " + bucket, 7);

        assertEquals(2, rows.size());
        assertEquals("0", rows.get(0)[0]);
        assertEquals("m", rows.get(0)[3]);
        assertEquals("100", rows.get(0)[5]);
        assertEquals("2000", rows.get(1)[0]);
        assertEquals("200", rows.get(1)[5]);
    }

    /** Rows still being collected are invisible to every serving query, whatever else they match. */
    @Test
    void servingQueries_readFoldedRowsOnly() throws SQLException
    {
        String uncompacted = "SELECT count(*) FROM server_metrics WHERE asset_id IN ('c9')";
        assertEquals(2L, scalar(uncompacted).longValue(), "the fixture holds sampling rows");

        assertEquals(0L, scalar("SELECT count(*) FROM server_metrics"
            + " WHERE compacted = 1 AND asset_id IN ('c9')").longValue());
        assertEquals(300L, scalar("SELECT SUM(output_tokens) FROM model_metrics"
            + " WHERE compacted = 1 AND asset_id = 'a1'").longValue(), "the 50-token sampling row is left out");
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
            + " WHERE compacted = 1 AND instant_ms >= 0 AND instant_ms < 3000 AND asset_id IN ('c1', 'c2')"
            + " ORDER BY container, cluster, namespace", 3);

        assertEquals(3, rows.size());
        assertArrayEqualsAsList(new String[] {"api", "c1", "ns1"}, rows.get(0));
        assertArrayEqualsAsList(new String[] {"api", "c2", "ns2"}, rows.get(1));
        assertArrayEqualsAsList(new String[] {"web", "c1", "ns1"}, rows.get(2));
    }

    @Test
    void sizeGauge_reportsThePositiveSizeOfTheDatabase()
    {
        double size = new PostgresSizeGauge(PostgresTestContainer.dataSource(jdbcUrl)).sizeBytes();

        assertTrue(size > 0, "expected a positive database size, got " + size);
    }

    /**
     * The derived tables carry an explicit alias: Postgres 15 and earlier reject a subquery in FROM
     * without one, so dropping it would break the query on those versions only.
     */
    private static String averageCpuPerBucket(long startMs, long endMs, long stepMs, String assetFilter)
    {
        String bucket = bucket(startMs, stepMs);
        return "SELECT " + bucket + " AS bucket, SUM(cpuPerInstant * spanMs) / NULLIF(SUM(spanMs), 0) AS cpu FROM ("
            + " SELECT instant_ms, SUM(cpu_nanocores) AS cpuPerInstant, MAX(window_ms) AS spanMs FROM server_metrics"
            + " WHERE compacted = 1 AND instant_ms >= " + startMs + " AND instant_ms < " + endMs + assetFilter
            + " GROUP BY instant_ms) AS per_instant"
            + " GROUP BY " + bucket + " ORDER BY " + bucket;
    }

    private static String containerSharesPerBucket(long startMs, long endMs, long stepMs, String assetFilter)
    {
        return "SELECT bucket, container,"
            + " SUM(cpuTime) * 1.0 / NULLIF(SUM(SUM(cpuTime)) OVER (PARTITION BY bucket), 0) AS share FROM ("
            + " SELECT " + bucket(startMs, stepMs) + " AS bucket, " + CONTAINER + " AS container,"
            + " cpu_nanocores * window_ms AS cpuTime FROM server_metrics"
            + " WHERE compacted = 1 AND instant_ms >= " + startMs + " AND instant_ms < " + endMs + assetFilter
            + ") AS per_row GROUP BY bucket, container ORDER BY bucket, container";
    }

    private static String bucket(long startMs, long stepMs)
    {
        return "(" + startMs + " + ((instant_ms - " + startMs + ") / " + stepMs + ") * " + stepMs + ")";
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
