package tech.illuin.wombat.engine.impact.llm;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.activity.llm.LLMServiceActivity;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.source.data.LLMData;
import tech.illuin.wombat.persistence.model.LLMMetricEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.illuin.wombat.core.activity.commons.Country.FRANCE;

@QuarkusTest
class LLMModelMetricRepositoryTest
{

    private static final long HOUR_MS = 3_600_000L;

    @Inject
    LLMModelMetricRepository repository;

    @BeforeEach
    @Transactional
    void clean()
    {
        repository.deleteAll();
    }

    @Test
    void save_persistsEntityWithLLMData()
    {
        repository.save(row(1000L, "p-llm", "mistral-large-latest", 42L));

        LLMMetricEntity persisted = repository.findAll().firstResult();
        assertEquals(new LLMData("mistral-large-latest", LLMProvider.mistralai, "mistral-large-latest", FRANCE, 42L, 1L), persisted.data);
        assertEquals(42L, persisted.outputTokens);
        assertEquals(1L, persisted.requestCount);
    }

    @Test
    void serviceActivitiesPerBucket_groupsByModelAndSumsTokensAndRequests()
    {
        repository.save(row(1000L, "p-llm", "mistral-large", 100L));
        repository.save(row(2000L, "p-llm", "mistral-large", 200L));
        repository.save(row(2000L, "p-llm", "gpt-4o", 500L));
        repository.save(row(2000L, "other", "gpt-4o", 999L));

        Map<String, LLMServiceActivity> activities = repository.serviceActivitiesPerBucket(0L, 5000L, 5000L, "p-llm").get(0L);
        assertEquals(2, activities.size());

        LLMServiceActivity mistral = activities.get("mistral-large");
        assertEquals(300L, mistral.outputTokenCount());
        assertEquals(2.0, mistral.requestCount());
        assertEquals(LLMProvider.mistralai, mistral.provider());
        assertEquals("mistral-large", mistral.model());

        LLMServiceActivity gpt = activities.get("gpt-4o");
        assertEquals(500L, gpt.outputTokenCount());
        assertEquals(1.0, gpt.requestCount());
    }

    /**
     * Buckets are laid from the start of the requested range, as the evaluation splits it into slots:
     * from 00:30 with an hourly step, a row at 01:00 belongs to the 00:30 bucket.
     */
    @Test
    void serviceActivitiesPerBucket_groupsRowsIntoBucketsAlignedOnTheStart()
    {
        long start = HOUR_MS / 2;
        repository.save(row(start, "p-llm", "mistral-large", 10L));
        repository.save(row(HOUR_MS, "p-llm", "mistral-large", 20L));
        repository.save(row(start + HOUR_MS, "p-llm", "gpt-4o", 7L));

        Map<Long, Map<String, LLMServiceActivity>> perHour = repository.serviceActivitiesPerBucket(start, start + 2 * HOUR_MS, HOUR_MS, "p-llm");

        assertEquals(List.of(start, start + HOUR_MS), List.copyOf(perHour.keySet()));
        assertEquals(30L, perHour.get(start).get("mistral-large").outputTokenCount());
        assertEquals(2.0, perHour.get(start).get("mistral-large").requestCount());
        assertEquals(Set.of("gpt-4o"), perHour.get(start + HOUR_MS).keySet());
    }

    /** The end is exclusive, so a row on the boundary of two buckets is only counted in the second. */
    @Test
    void serviceActivitiesPerBucket_countsABoundaryRowInOneBucketOnly()
    {
        repository.save(row(HOUR_MS, "p-llm", "m", 10L));

        assertTrue(repository.serviceActivitiesPerBucket(0L, HOUR_MS, HOUR_MS, "p-llm").isEmpty());
        assertEquals(Set.of(HOUR_MS), repository.serviceActivitiesPerBucket(0L, 2 * HOUR_MS, HOUR_MS, "p-llm").keySet());
    }

    @Test
    void serviceActivitiesPerBucket_ignoresRowsNotYetCompacted()
    {
        repository.save(sampled(1000L, "p-llm", "m", 10L));

        assertTrue(repository.serviceActivitiesPerBucket(0L, HOUR_MS, HOUR_MS, "p-llm").isEmpty());
    }

    /** Tokens and requests are totals, so folding a window sums them rather than averaging. */
    @Test
    void compactBucket_foldsTheWindowIntoOneRowPerModel()
    {
        repository.save(sampled(0L, "p-llm", "m", 10L, 2L));
        repository.save(sampled(1000L, "p-llm", "m", 20L, 3L));
        repository.save(sampled(2000L, "other", "m", 5L, 1L));

        int rows = repository.compactBucket(0L, HOUR_MS);

        assertEquals(2, rows);
        assertEquals(30L, servedTokens("p-llm"));
        assertEquals(5L, servedTokens("other"));

        List<LLMMetricEntity> all = repository.findAll().list();
        assertEquals(2, all.size(), "the sampling rows should be gone");
        assertTrue(all.stream().allMatch(row -> row.compacted));
        assertTrue(all.stream().allMatch(row -> row.instantMs == 0L));

        LLMMetricEntity pllm = all.stream().filter(r -> "p-llm".equals(r.assetId)).findFirst().orElseThrow();
        assertEquals(30L, pllm.outputTokens);
        assertEquals(5L, pllm.requestCount);
        assertEquals(30L, pllm.data.outputTokens());
        assertEquals(5L, pllm.data.requests());

        LLMMetricEntity other = all.stream().filter(r -> "other".equals(r.assetId)).findFirst().orElseThrow();
        assertEquals(5L, other.outputTokens);
        assertEquals(1L, other.requestCount);
        assertEquals(5L, other.data.outputTokens());
        assertEquals(1L, other.data.requests());
    }

    @Test
    void compactBucket_foldsDifferentModelsIntoSeparateRows()
    {
        repository.save(sampled(0L, "p-llm", "mistral", 10L, 2L));
        repository.save(sampled(1000L, "p-llm", "gpt", 20L, 4L));
        repository.save(sampled(2000L, "p-llm", "mistral", 5L, 1L));

        int rows = repository.compactBucket(0L, HOUR_MS);

        assertEquals(2, rows);
        List<LLMMetricEntity> all = repository.findAll().list();
        assertEquals(2, all.size());

        LLMMetricEntity mistral = all.stream().filter(r -> "mistral".equals(r.data.model())).findFirst().orElseThrow();
        assertEquals(15L, mistral.outputTokens);
        assertEquals(3L, mistral.requestCount);
        assertEquals(15L, mistral.data.outputTokens());
        assertEquals(3L, mistral.data.requests());

        LLMMetricEntity gpt = all.stream().filter(r -> "gpt".equals(r.data.model())).findFirst().orElseThrow();
        assertEquals(20L, gpt.outputTokens);
        assertEquals(4L, gpt.requestCount);
        assertEquals(20L, gpt.data.outputTokens());
        assertEquals(4L, gpt.data.requests());
    }

    @Test
    void compactBucket_mergesASampleThatArrivesAfterTheWindowWasFolded()
    {
        repository.save(sampled(0L, "p-llm", "m", 10L, 2L));
        repository.save(sampled(1000L, "p-llm", "m", 20L, 3L));
        repository.compactBucket(0L, HOUR_MS);

        repository.save(sampled(2000L, "p-llm", "m", 15L, 1L));
        assertEquals(1, repository.compactBucket(0L, HOUR_MS));

        List<LLMMetricEntity> folded = repository.findAll().list();
        assertEquals(1, folded.size(), "one row for the hour, not two");
        LLMMetricEntity entity = folded.getFirst();
        assertEquals(45L, entity.outputTokens);
        assertEquals(6L, entity.requestCount);
        assertEquals(45L, entity.data.outputTokens());
        assertEquals(6L, entity.data.requests());
        assertEquals(45L, servedTokens("p-llm"));
    }

    @Test
    void compactBucket_leavesTheOtherWindowsAlone()
    {
        repository.save(sampled(0L, "p-llm", "m", 10L, 1L));
        repository.save(sampled(HOUR_MS, "p-llm", "m", 20L, 2L));

        repository.compactBucket(0L, HOUR_MS);

        List<LLMMetricEntity> all = repository.findAll().list();
        assertEquals(2, all.size());

        LLMMetricEntity firstHour = all.stream().filter(r -> r.instantMs == 0L).findFirst().orElseThrow();
        assertTrue(firstHour.compacted);
        assertEquals(10L, firstHour.outputTokens);
        assertEquals(1L, firstHour.requestCount);

        LLMMetricEntity secondHour = all.stream().filter(r -> r.instantMs == HOUR_MS).findFirst().orElseThrow();
        assertFalse(secondHour.compacted);
        assertEquals(20L, secondHour.outputTokens);
        assertEquals(2L, secondHour.requestCount);
    }

    @Test
    void compactBucket_isIdempotent()
    {
        repository.save(sampled(0L, "p-llm", "m", 10L, 2L));
        repository.compactBucket(0L, HOUR_MS);

        assertEquals(0, repository.compactBucket(0L, HOUR_MS));
        assertEquals(10L, servedTokens("p-llm"));
        assertEquals(1, repository.findAll().list().size());
    }

    @Test
    void uncompactedBuckets_listsTheWindowsStillHoldingSamplingRowsBeforeTheCutoff()
    {
        repository.save(sampled(0L, "p-llm", "m", 1L));
        repository.save(sampled(2 * HOUR_MS, "p-llm", "m", 1L));
        repository.save(sampled(5 * HOUR_MS, "p-llm", "m", 1L));

        assertEquals(List.of(0L, 2 * HOUR_MS), repository.uncompactedBuckets(HOUR_MS, 4 * HOUR_MS));
    }

    /** A folded row, the kind every serving query reads. */
    private long servedTokens(String assetId)
    {
        return repository.serviceActivitiesPerBucket(0L, HOUR_MS, HOUR_MS, assetId).getOrDefault(0L, Map.of()).values().stream()
            .mapToLong(LLMServiceActivity::outputTokenCount)
            .sum();
    }

    private static LLMMetricEntity row(long instantMs, String assetId, String model, long outputTokens)
    {
        return row(instantMs, assetId, model, outputTokens, 1L);
    }

    private static LLMMetricEntity row(long instantMs, String assetId, String model, long outputTokens, long requestCount)
    {
        LLMMetricEntity row = sampled(instantMs, assetId, model, outputTokens, requestCount);
        row.compacted = true;
        return row;
    }

    /** A sampling row as collection writes it, waiting to be folded. */
    private static LLMMetricEntity sampled(long instantMs, String assetId, String model, long outputTokens)
    {
        return sampled(instantMs, assetId, model, outputTokens, 1L);
    }

    private static LLMMetricEntity sampled(long instantMs, String assetId, String model, long outputTokens, long requestCount)
    {
        LLMMetricEntity row = new LLMMetricEntity();
        row.instantMs = instantMs;
        row.assign(new AssetIdentity(assetId, "env", assetId + " name"));
        row.assetType = "tech.illuin.wombat-module.llm-prometheus";
        row.data = new LLMData(model, LLMProvider.mistralai, model, FRANCE, outputTokens, requestCount);
        row.outputTokens = outputTokens;
        row.requestCount = requestCount;
        return row;
    }
}
