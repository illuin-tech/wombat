package tech.illuin.wombat.impact.kubernetes;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.core.evaluation.impact.kubernetes.ContainerLocation;
import tech.illuin.wombat.core.evaluation.impact.kubernetes.KubernetesMetricResolver;
import tech.illuin.wombat.persistence.dialect.JsonPathDialect;
import tech.illuin.wombat.persistence.model.KubernetesMetricEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

@ApplicationScoped
public class KubernetesMetricRepository implements PanacheRepositoryBase<KubernetesMetricEntity, Long>, KubernetesMetricResolver
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
            "SELECT * FROM server_metrics WHERE instantMs >= :start AND instantMs <= :end");
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
            "SELECT AVG(perInstant) FROM ("
            + " SELECT SUM(cpu_nanocores) AS perInstant FROM server_metrics"
            + " WHERE instantMs >= :start AND instantMs <= :end");
        this.appendClusterFilter(sql, clusterIds);
        sql.append(" GROUP BY instantMs) AS per_instant");

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
            + " SUM(cpu_nanocores) * 1.0 / SUM(SUM(cpu_nanocores)) OVER () AS share FROM server_metrics"
            + " WHERE instantMs >= :start AND instantMs <= :end");
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
    public Map<String, List<ContainerLocation>> containerLocations(long startMs, long endMs, List<String> clusterIds)
    {
        StringBuilder sql = new StringBuilder(
            "SELECT DISTINCT " + this.dialect.text(DATA, SERVICE_ID) + " AS container,"
            + " " + this.dialect.text(DATA, "cluster") + " AS cluster,"
            + " " + this.dialect.text(DATA, "namespace") + " AS namespace FROM server_metrics"
            + " WHERE instantMs >= :start AND instantMs <= :end");
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
}
