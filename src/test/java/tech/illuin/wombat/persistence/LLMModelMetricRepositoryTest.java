package tech.illuin.wombat.persistence;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.source.data.LLMData;
import tech.illuin.wombat.impact.llm.LLMModelMetricRepository;
import tech.illuin.wombat.persistence.model.LLMMetricEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(new LLMData("mistral-large-latest", "p-llm", "env", "mistral-large-latest", 42L), persisted.data);
        assertEquals(42L, persisted.outputTokens);
    }

    @Test
    void sumOutputTokens_sumsDeltasWithinRangeForProfile()
    {
        repository.save(row(1000L, "p-llm", "m", 10L));
        repository.save(row(2000L, "p-llm", "m", 20L));
        repository.save(row(3000L, "p-llm", "m", 30L));

        assertEquals(30L, repository.sumOutputTokens(1000L, 2000L, "p-llm"));
        assertEquals(60L, repository.sumOutputTokens(0L, 5000L, "p-llm"));
    }

    @Test
    void sumOutputTokens_filtersByProfile()
    {
        repository.save(row(1000L, "p-llm", "m", 10L));
        repository.save(row(1000L, "other", "m", 99L));

        assertEquals(10L, repository.sumOutputTokens(0L, 5000L, "p-llm"));
    }

    /** Tokens are a total, so a bucket is simply the sum of the rows landing in it. */
    @Test
    void outputTokensPerBucket_sumsTheTokensOfEachBucket()
    {
        repository.save(row(0L, "p-llm", "m", 10L));
        repository.save(row(1000L, "p-llm", "m", 20L));
        repository.save(row(HOUR_MS + 5L, "p-llm", "m", 7L));
        repository.save(row(0L, "other", "m", 99L));

        Map<Long, Double> perHour = repository.outputTokensPerBucket(0L, 2 * HOUR_MS, HOUR_MS, "p-llm", List.of());

        assertEquals(2, perHour.size());
        assertEquals(30.0, perHour.get(0L), 1e-6);
        assertEquals(7.0, perHour.get(HOUR_MS), 1e-6);
    }

    /**
     * An asset serving several models, filtered down to one: the buckets have to follow that model's
     * tokens alone, or the page would spread its footprint over hours another model was busy in.
     */
    @Test
    void outputTokensPerBucket_withAServiceFilter_countsOnlyTheSelectedModels()
    {
        repository.save(row(0L, "p-llm", "mistral", 10L));
        repository.save(row(HOUR_MS, "p-llm", "gpt", 40L));
        repository.save(row(HOUR_MS, "p-llm", "mistral", 5L));

        Map<Long, Double> perHour = repository.outputTokensPerBucket(0L, 2 * HOUR_MS, HOUR_MS, "p-llm", List.of("mistral"));

        assertEquals(2, perHour.size());
        assertEquals(10.0, perHour.get(0L), 1e-6);
        assertEquals(5.0, perHour.get(HOUR_MS), 1e-6, "the other model's tokens should not land in this bucket");
    }

    /** An empty filter is "no filter": the page reporting on every model spreads over every model. */
    @Test
    void outputTokensPerBucket_withoutAServiceFilter_countsEveryModel()
    {
        repository.save(row(0L, "p-llm", "mistral", 10L));
        repository.save(row(0L, "p-llm", "gpt", 40L));

        assertEquals(50.0, repository.outputTokensPerBucket(0L, HOUR_MS, HOUR_MS, "p-llm", List.of()).get(0L), 1e-6);
    }

    @Test
    void sumOutputTokens_noRows_returnsZero()
    {
        assertEquals(0L, repository.sumOutputTokens(0L, 5000L, "p-llm"));
    }

    @Test
    void sumOutputTokens_ignoresRowsNotYetCompacted()
    {
        repository.save(sampled(1000L, "p-llm", "m", 10L));

        assertEquals(0L, repository.sumOutputTokens(0L, 5000L, "p-llm"));
        assertTrue(repository.outputTokensPerBucket(0L, HOUR_MS, HOUR_MS, "p-llm", List.of()).isEmpty());
    }

    /** Tokens are a total, so folding a window sums them rather than averaging. */
    @Test
    void compactBucket_foldsTheWindowIntoOneRowPerModel()
    {
        repository.save(sampled(0L, "p-llm", "m", 10L));
        repository.save(sampled(1000L, "p-llm", "m", 20L));
        repository.save(sampled(2000L, "other", "m", 5L));

        int rows = repository.compactBucket(0L, HOUR_MS);

        assertEquals(2, rows);
        assertEquals(30L, repository.sumOutputTokens(0L, HOUR_MS, "p-llm"));
        assertEquals(5L, repository.sumOutputTokens(0L, HOUR_MS, "other"));
        assertEquals(2, repository.findAll().list().size(), "the sampling rows should be gone");
    }

    @Test
    void compactBucket_isIdempotent()
    {
        repository.save(sampled(0L, "p-llm", "m", 10L));
        repository.compactBucket(0L, HOUR_MS);

        assertEquals(0, repository.compactBucket(0L, HOUR_MS));
        assertEquals(10L, repository.sumOutputTokens(0L, HOUR_MS, "p-llm"));
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
    private static LLMMetricEntity row(long instantMs, String profileId, String model, long outputTokens)
    {
        LLMMetricEntity row = sampled(instantMs, profileId, model, outputTokens);
        row.compacted = true;
        return row;
    }

    /** A sampling row as collection writes it, waiting to be folded. */
    private static LLMMetricEntity sampled(long instantMs, String profileId, String model, long outputTokens)
    {
        LLMMetricEntity row = new LLMMetricEntity();
        row.instantMs = instantMs;
        row.data = new LLMData(model, profileId, "env", model, outputTokens);
        row.outputTokens = outputTokens;
        return row;
    }
}
