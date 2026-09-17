package tech.illuin.wombat.persistence;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.source.data.LLMData;
import tech.illuin.wombat.impact.llm.LLMModelMetricRepository;
import tech.illuin.wombat.persistence.model.LLMMetricEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class LLMModelMetricRepositoryTest
{

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

    @Test
    void sumOutputTokens_noRows_returnsZero()
    {
        assertEquals(0L, repository.sumOutputTokens(0L, 5000L, "p-llm"));
    }

    private static LLMMetricEntity row(long instantMs, String profileId, String model, long outputTokens)
    {
        LLMMetricEntity row = new LLMMetricEntity();
        row.instantMs = instantMs;
        row.data = new LLMData(model, profileId, "env", model, outputTokens);
        row.outputTokens = outputTokens;
        return row;
    }
}
