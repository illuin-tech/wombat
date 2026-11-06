package tech.illuin.wombat.engine.impact.llm;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.persistence.compaction.BucketCompactor;
import tech.illuin.wombat.core.activity.commons.Country;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.activity.llm.LLMServiceActivity;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.evaluation.impact.llm.LLMMetricResolver;
import tech.illuin.wombat.persistence.dialect.JsonPathDialect;
import tech.illuin.wombat.core.source.data.LLMData;
import tech.illuin.wombat.engine.impact.commons.MetricBuckets;
import tech.illuin.wombat.persistence.model.LLMMetricEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static tech.illuin.wombat.core.activity.commons.TimeRange.toEpochMs;

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

    @Transactional
    public void save(Collection<LLMMetricEntity> entities)
    {
        this.persist(entities);
    }

    @Override @Transactional
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

    @Override @Transactional
    public int compactBucket(long bucketStartMs, long stepMs)
    {
        long bucketEndMs = bucketStartMs + stepMs;
        List<LLMMetricEntity> rows = find("instantMs >= ?1 AND instantMs < ?2", bucketStartMs, bucketEndMs).list();
        if (rows.stream().allMatch(row -> row.compacted))
            return 0;

        Map<ModelKey, List<LLMMetricEntity>> byModel = new LinkedHashMap<>();
        for (LLMMetricEntity row : rows)
            byModel.computeIfAbsent(ModelKey.of(row), key -> new ArrayList<>()).add(row);

        List<LLMMetricEntity> folded = new ArrayList<>(byModel.size());
        byModel.forEach((key, samples) -> {
            LLMMetricEntity row = new LLMMetricEntity();
            row.instantMs = bucketStartMs;
            row.outputTokens = samples.stream().mapToLong(sample -> sample.outputTokens).sum();
            row.requestCount = samples.stream().mapToLong(sample -> sample.requestCount).sum();
            row.assign(key.asset());
            row.assetType = key.assetType();
            row.data = key.toData(row.outputTokens, row.requestCount);
            row.compacted = true;
            folded.add(row);
        });

        delete("instantMs >= ?1 AND instantMs < ?2", bucketStartMs, bucketEndMs);
        getEntityManager().clear();

        folded.forEach(this::persist);
        return folded.size();
    }

    @Override
    public Map<Long, Map<String, LLMServiceActivity>> serviceActivitiesPerBucket(TimeRange range, String assetId)
    {
        List<TimeRange> slots = range.split();
        long startMs = toEpochMs(range.start());
        long endMs = toEpochMs(slots.getLast().end());
        long stepMs = range.resolution().toMillis();
        String bucket = MetricBuckets.expression(startMs, stepMs);
        String serviceIdCol = this.dialect.text("data", "serviceId");
        String providerCol = this.dialect.text("data", "provider");
        String modelCol = this.dialect.text("data", "model");
        String locationCol = this.dialect.text("data", "location");

        String sql = """
            SELECT %5$s AS bucket, %1$s AS serviceId, %2$s AS provider, %3$s AS model, %4$s AS location,
               SUM(output_tokens) AS tokens,
               SUM(request_count) AS requests
            FROM model_metrics
            WHERE compacted = 1
              AND instant_ms >= :start
              AND instant_ms < :end
              AND asset_id = :assetId
            GROUP BY %5$s, %1$s, %2$s, %3$s, %4$s
            ORDER BY %5$s
        """.formatted(serviceIdCol, providerCol, modelCol, locationCol, bucket);

        Query query = getEntityManager().createNativeQuery(sql);
        query.setParameter("start", startMs);
        query.setParameter("end", endMs);
        query.setParameter("assetId", assetId);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        Map<Long, Map<String, LLMServiceActivity>> perBucket = new LinkedHashMap<>();
        for (Object[] row : rows)
        {
            long bucketStart = ((Number) row[0]).longValue();
            String serviceId = (String) row[1];
            String providerStr = (String) row[2];
            LLMProvider provider = (providerStr != null && !providerStr.isBlank()) ? LLMProvider.valueOf(providerStr) : null;
            String model = (String) row[3];
            String location = (String) row[4];
            Country locationCountry = Country.ofCode(location).orElseThrow(() -> new IllegalArgumentException("Cannot determine country from location: " + location));
            long tokens = row[5] != null ? ((Number) row[5]).longValue() : 0L;
            double requests = row[6] != null ? ((Number) row[6]).doubleValue() : 1.0;

            perBucket.computeIfAbsent(bucketStart, b -> new LinkedHashMap<>()).merge(serviceId, new LLMServiceActivity(provider, model, locationCountry, tokens, requests),
                (oldVal, newVal) -> new LLMServiceActivity(
                    newVal.provider() != null ? newVal.provider() : oldVal.provider(),
                    newVal.model() != null ? newVal.model() : oldVal.model(),
                    newVal.location() != null ? newVal.location() : oldVal.location(),
                    oldVal.outputTokenCount() + newVal.outputTokenCount(),
                    oldVal.requestCount() + newVal.requestCount()
                )
            );
        }
        return perBucket;
    }

    private record ModelKey(
        AssetIdentity asset,
        String assetType,
        String serviceId,
        LLMProvider provider,
        String model,
        Country location
    ) {
        private static ModelKey of(LLMMetricEntity row)
        {
            LLMData data = row.data;
            return new ModelKey(row.identity(), row.assetType, data.serviceId(), data.provider(), data.model(), data.location());
        }

        private LLMData toData(long outputTokens, long requests)
        {
            return new LLMData(this.serviceId, this.provider, this.model, this.location, outputTokens, requests);
        }
    }
}
