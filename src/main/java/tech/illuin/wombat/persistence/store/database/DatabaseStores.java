package tech.illuin.wombat.persistence.store.database;

import tech.illuin.wombat.engine.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.engine.impact.llm.LLMModelMetricRepository;
import tech.illuin.wombat.persistence.store.MetricStores;

public final class DatabaseStores
{
    private DatabaseStores() {}

    public static MetricStores of(KubernetesMetricRepository kubernetesRepository, LLMModelMetricRepository llmRepository)
    {
        return new MetricStores(
            new KubernetesDatabaseStore(kubernetesRepository),
            new LLMDatabaseStore(llmRepository)
        );
    }
}
