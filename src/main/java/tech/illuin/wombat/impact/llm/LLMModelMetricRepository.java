package tech.illuin.wombat.impact.llm;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.core.compaction.BucketCompactor;
import tech.illuin.wombat.core.evaluation.impact.llm.LLMMetricResolver;
import tech.illuin.wombat.persistence.dialect.JsonPathDialect;
import tech.illuin.wombat.core.source.data.LLMData;
import tech.illuin.wombat.impact.commons.MetricBuckets;
import tech.illuin.wombat.persistence.model.LLMMetricEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class LLMModelMetricRepository implements PanacheRepositoryBase<LLMMetricEntity, Long>, LLMMetricResolver, BucketCompactor
{
    private final JsonPathDialect dialect;

    public LLMModelMetricRepository(JsonPathDialect dialect)
    {
        this.dialect = dialect;
    }

    @Transactional
    public void save(LLMMetricEntity entity)
    {
        this.persist(entity);
    }


    @Override
    public Map<Long, Double> outputTokensPerBucket(long startMs, long endMs, long stepMs, String assetId, Collection<String> serviceIds)
    {
        String bucket = MetricBuckets.expression(stepMs);
        StringBuilder sql = new StringBuilder(
            "SELECT " + bucket + " AS bucket, SUM(output_tokens) AS tokens FROM model_metrics"
            + " WHERE compacted = 1 AND instant_ms >= :start AND instant_ms <= :end"
            + " AND " + this.dialect.text("data", "assetId") + " = :assetId");
        if (!serviceIds.isEmpty())
            sql.append(" AND ").append(this.dialect.text("data", "model")).append(" IN (:services)");
        sql.append(" GROUP BY ").append(bucket).append(" ORDER BY ").append(bucket);

        Query query = getEntityManager().createNativeQuery(sql.toString());
        query.setParameter("start", startMs);
        query.setParameter("end", endMs);
        query.setParameter("assetId", assetId);
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
    public List<Long> uncompactedBuckets(long stepMs, long beforeMs)
    {
        String bucket = MetricBuckets.expression(stepMs);
        Query query = getEntityManager().createNativeQuery(
            "SELECT DISTINCT " + bucket + " AS bucket FROM model_metrics"
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
        List<LLMMetricEntity> rows = find("instantMs >= ?1 AND instantMs < ?2", bucketStartMs, bucketEndMs).list();
        if (rows.stream().allMatch(row -> row.compacted))
            return 0;

        Map<ModelKey, Long> byModel = new LinkedHashMap<>();
        for (LLMMetricEntity row : rows)
            byModel.merge(ModelKey.of(row), row.outputTokens, Long::sum);

        List<LLMMetricEntity> folded = new ArrayList<>(byModel.size());
        byModel.forEach((key, tokens) -> {
            LLMMetricEntity row = new LLMMetricEntity();
            row.instantMs = bucketStartMs;
            row.outputTokens = tokens;
            row.data = key.toData(tokens);
            row.compacted = true;
            folded.add(row);
        });

        delete("instantMs >= ?1 AND instantMs < ?2", bucketStartMs, bucketEndMs);
        getEntityManager().clear();

        folded.forEach(this::persist);
        return folded.size();
    }

    @Override
    public long sumOutputTokens(long startMs, long endMs, String assetId)
    {
        Query query = getEntityManager().createNativeQuery(
            "SELECT COALESCE(SUM(output_tokens), 0) FROM model_metrics"
            + " WHERE compacted = 1 AND instant_ms >= :start AND instant_ms <= :end"
            + " AND " + this.dialect.text("data", "assetId") + " = :assetId");
        query.setParameter("start", startMs);
        query.setParameter("end", endMs);
        query.setParameter("assetId", assetId);
        return ((Number) query.getSingleResult()).longValue();
    }

    private record ModelKey(
        String serviceId,
        String assetId,
        String environmentId,
        String model
    ) {
        private static ModelKey of(LLMMetricEntity row)
        {
            LLMData data = row.data;
            return new ModelKey(data.serviceId(), data.assetId(), data.environmentId(), data.model());
        }

        private LLMData toData(long outputTokens)
        {
            return new LLMData(this.serviceId, this.assetId, this.environmentId, this.model, outputTokens);
        }
    }
}
