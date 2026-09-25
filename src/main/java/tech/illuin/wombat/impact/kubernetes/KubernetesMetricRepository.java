package tech.illuin.wombat.impact.kubernetes;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.core.compaction.BucketCompactor;
import tech.illuin.wombat.core.evaluation.impact.kubernetes.ContainerLocation;
import tech.illuin.wombat.core.evaluation.impact.kubernetes.KubernetesMetricResolver;
import tech.illuin.wombat.impact.commons.MetricBuckets;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.persistence.dialect.JsonPathDialect;
import tech.illuin.wombat.persistence.model.KubernetesMetricEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.ToDoubleFunction;

@ApplicationScoped
public class KubernetesMetricRepository implements PanacheRepositoryBase<KubernetesMetricEntity, Long>, KubernetesMetricResolver, BucketCompactor
{
    private static final String DATA = "data";
    private static final String SERVICE_ID = "serviceId";

    private final JsonPathDialect dialect;

    public KubernetesMetricRepository(JsonPathDialect dialect)
    {
        this.dialect = dialect;
    }

    @Transactional
    public void save(KubernetesMetricEntity entity)
    {
        persist(entity);
    }

    public List<KubernetesMetricEntity> findByRange(long startMs, long endMs)
    {
        return find("instantMs >= ?1 AND instantMs <= ?2", startMs, endMs).list();
    }

    public List<KubernetesMetricEntity> findByRangeAndClusters(long startMs, long endMs, List<String> clusterIds)
    {
        if (clusterIds.isEmpty()) return findByRange(startMs, endMs);
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM server_metrics WHERE instant_ms >= :start AND instant_ms <= :end");
        this.appendClusterFilter(sql, clusterIds);

        Query query = bind(getEntityManager().createNativeQuery(sql.toString(), KubernetesMetricEntity.class), startMs, endMs, clusterIds);
        @SuppressWarnings("unchecked")
        List<KubernetesMetricEntity> rows = query.getResultList();
        return rows;
    }

    @Override
    public OptionalDouble averageCpuPerInstant(long startMs, long endMs, List<String> clusterIds)
    {
        StringBuilder sql = new StringBuilder(
            "SELECT SUM(cpuPerInstant * spanMs) / NULLIF(SUM(spanMs), 0) FROM ("
            + " SELECT SUM(cpu_nanocores) AS cpuPerInstant, MAX(window_ms) AS spanMs FROM server_metrics"
            + " WHERE compacted = 1 AND instant_ms >= :start AND instant_ms <= :end");
        this.appendClusterFilter(sql, clusterIds);
        sql.append(" GROUP BY instant_ms) AS per_instant");

        Query query = bind(getEntityManager().createNativeQuery(sql.toString()), startMs, endMs, clusterIds);
        Object result = query.getSingleResult();
        return result == null ? OptionalDouble.empty() : OptionalDouble.of(((Number) result).doubleValue());
    }

    @Override
    public Map<String, Double> containerShares(long startMs, long endMs, List<String> clusterIds)
    {
        String container = this.dialect.text(DATA, SERVICE_ID);
        StringBuilder sql = new StringBuilder(
            "SELECT " + container + " AS container,"
            + " SUM(cpu_nanocores * window_ms) * 1.0 / NULLIF(SUM(SUM(cpu_nanocores * window_ms)) OVER (), 0) AS share FROM server_metrics"
            + " WHERE compacted = 1 AND instant_ms >= :start AND instant_ms <= :end");
        this.appendClusterFilter(sql, clusterIds);
        sql.append(" GROUP BY ").append(container);

        Query query = bind(getEntityManager().createNativeQuery(sql.toString()), startMs, endMs, clusterIds);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        Map<String, Double> shares = new LinkedHashMap<>();
        for (Object[] row : rows)
        {
            if (row[1] == null) return Map.of();
            shares.put((String) row[0], ((Number) row[1]).doubleValue());
        }
        return shares;
    }

    @Override
    public Map<Long, Double> cpuTimePerBucket(long startMs, long endMs, long stepMs, List<String> clusterIds, Collection<String> serviceIds)
    {
        String bucket = MetricBuckets.expression(stepMs);
        StringBuilder sql = new StringBuilder(
            "SELECT " + bucket + " AS bucket, SUM(cpu_nanocores * window_ms) AS cpuTime FROM server_metrics"
            + " WHERE compacted = 1 AND instant_ms >= :start AND instant_ms <= :end");
        this.appendClusterFilter(sql, clusterIds);
        if (!serviceIds.isEmpty())
            sql.append(" AND ").append(this.dialect.text(DATA, SERVICE_ID)).append(" IN (:services)");
        sql.append(" GROUP BY ").append(bucket).append(" ORDER BY ").append(bucket);

        Query query = bind(getEntityManager().createNativeQuery(sql.toString()), startMs, endMs, clusterIds);
        if (!serviceIds.isEmpty())
            query.setParameter("services", serviceIds);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        Map<Long, Double> perBucket = new LinkedHashMap<>();
        for (Object[] row : rows)
        {
            if (row[1] == null) continue;
            perBucket.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue());
        }
        return perBucket;
    }

    @Override
    public Map<String, List<ContainerLocation>> containerLocations(long startMs, long endMs, List<String> clusterIds)
    {
        StringBuilder sql = new StringBuilder(
            "SELECT DISTINCT " + this.dialect.text(DATA, SERVICE_ID) + " AS container,"
            + " " + this.dialect.text(DATA, "cluster") + " AS cluster,"
            + " " + this.dialect.text(DATA, "namespace") + " AS namespace FROM server_metrics"
            + " WHERE compacted = 1 AND instant_ms >= :start AND instant_ms <= :end");
        this.appendClusterFilter(sql, clusterIds);
        sql.append(" ORDER BY container, cluster, namespace");

        Query query = bind(getEntityManager().createNativeQuery(sql.toString()), startMs, endMs, clusterIds);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        Map<String, List<ContainerLocation>> locations = new LinkedHashMap<>();
        for (Object[] row : rows)
        {
            locations.computeIfAbsent((String) row[0], k -> new ArrayList<>())
                .add(new ContainerLocation((String) row[1], (String) row[2]));
        }
        return locations;
    }

    @Override
    public List<Long> uncompactedBuckets(long stepMs, long beforeMs)
    {
        String bucket = MetricBuckets.expression(stepMs);
        Query query = getEntityManager().createNativeQuery(
            "SELECT DISTINCT " + bucket + " AS bucket FROM server_metrics"
            + " WHERE compacted = 0 AND instant_ms < :before ORDER BY " + bucket);
        query.setParameter("before", beforeMs);
        @SuppressWarnings("unchecked")
        List<Object> rows = query.getResultList();
        return rows.stream().map(row -> ((Number) row).longValue()).toList();
    }

    @Transactional @Override
    public int compactBucket(long bucketStartMs, long stepMs)
    {
        long bucketEndMs = bucketStartMs + stepMs;
        List<KubernetesMetricEntity> rows = find(
            "instantMs >= ?1 AND instantMs < ?2", bucketStartMs, bucketEndMs).list();
        if (rows.stream().noneMatch(row -> !row.compacted))
            return 0;

        Map<ContainerKey, List<KubernetesMetricEntity>> byContainer = new LinkedHashMap<>();
        for (KubernetesMetricEntity row : rows)
            byContainer.computeIfAbsent(ContainerKey.of(row), key -> new ArrayList<>()).add(row);

        // Built before the window is cleared out, since they are folded from the rows about to go.
        List<KubernetesMetricEntity> folded = new ArrayList<>(byContainer.size());
        byContainer.forEach((key, samples) -> {
            KubernetesMetricEntity row = new KubernetesMetricEntity();
            row.instantMs = bucketStartMs;
            row.windowMs = samples.stream().mapToLong(sample -> sample.windowMs).sum();
            row.cpuNanocores = weightedMean(samples, sample -> sample.cpuNanocores);
            row.ramBytes = weightedMean(samples, sample -> sample.ramBytes);
            row.data = key.toData(row.cpuNanocores, row.ramBytes);
            row.compacted = true;
            folded.add(row);
        });

        delete("instantMs >= ?1 AND instantMs < ?2", bucketStartMs, bucketEndMs);
        getEntityManager().clear();

        folded.forEach(this::persist);
        return folded.size();
    }

    private static double weightedMean(List<KubernetesMetricEntity> rows, ToDoubleFunction<KubernetesMetricEntity> value)
    {
        double weighted = 0.0;
        double span = 0.0;
        for (KubernetesMetricEntity row : rows)
        {
            weighted += value.applyAsDouble(row) * row.windowMs;
            span += row.windowMs;
        }
        return span > 0 ? weighted / span : 0.0;
    }


    private void appendClusterFilter(StringBuilder sql, List<String> clusterIds)
    {
        if (!clusterIds.isEmpty()) sql.append(" AND ").append(this.dialect.text(DATA, "cluster")).append(" IN (:clusters)");
    }

    private static Query bind(Query query, long startMs, long endMs, List<String> clusterIds)
    {
        query.setParameter("start", startMs);
        query.setParameter("end", endMs);
        if (!clusterIds.isEmpty()) query.setParameter("clusters", clusterIds);
        return query;
    }

    private record ContainerKey(
        String serviceId,
        String assetId,
        String environmentId,
        String cluster,
        String namespace,
        String pod
    ) {
        private static ContainerKey of(KubernetesMetricEntity row)
        {
            KubernetesData data = row.data;
            return new ContainerKey(
                data.serviceId(), data.assetId(), data.environmentId(), data.cluster(), data.namespace(), data.pod());
        }

        private KubernetesData toData(double cpuNanocores, double ramBytes)
        {
            return new KubernetesData(
                this.serviceId, this.assetId, this.environmentId, this.cluster, this.namespace, this.pod,
                cpuNanocores, ramBytes);
        }
    }
}
