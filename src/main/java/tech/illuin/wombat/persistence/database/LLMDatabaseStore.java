package tech.illuin.wombat.persistence.database;

import tech.illuin.wombat.core.source.data.LLMData;
import tech.illuin.wombat.engine.impact.llm.LLMModelMetricRepository;
import tech.illuin.wombat.persistence.model.LLMMetricEntity;
import tech.illuin.wombat.persistence.backend.api.store.MetricSample;
import tech.illuin.wombat.persistence.backend.api.store.MetricStore;

import java.util.Collection;

public class LLMDatabaseStore implements MetricStore<LLMData>
{
    private final LLMModelMetricRepository repository;

    public LLMDatabaseStore(LLMModelMetricRepository repository)
    {
        this.repository = repository;
    }

    @Override
    public void save(Collection<MetricSample<LLMData>> samples)
    {
        this.repository.save(samples.stream().map(LLMDatabaseStore::toEntity).toList());
    }

    private static LLMMetricEntity toEntity(MetricSample<LLMData> sample)
    {
        LLMMetricEntity row = new LLMMetricEntity();
        row.instantMs = sample.instantMs();
        row.assign(sample.asset());
        row.data = sample.data();
        row.outputTokens = sample.data().outputTokens();
        return row;
    }
}
