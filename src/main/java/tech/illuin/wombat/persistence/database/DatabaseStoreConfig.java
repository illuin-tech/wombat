package tech.illuin.wombat.persistence.database;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.engine.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.engine.impact.llm.LLMModelMetricRepository;
import tech.illuin.wombat.persistence.backend.api.store.MetricStoreContainer;

import java.util.Map;

@ApplicationScoped
public class DatabaseStoreConfig
{
    @Produces @Singleton
    public MetricStoreContainer provideDatabaseStores(
        KubernetesMetricRepository kubernetesRepository,
        LLMModelMetricRepository llmRepository
    ) {
        return new MetricStoreContainer(Map.of(
            ServiceFamily.KUBERNETES_CONTAINER, new KubernetesDatabaseStore(kubernetesRepository),
            ServiceFamily.LLM, new LLMDatabaseStore(llmRepository)
        ));
    }
}
