package tech.illuin.wombat.engine.impact;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import tech.illuin.wombat.engine.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.engine.impact.llm.LLMModelMetricRepository;
import tech.illuin.wombat.engine.impact.timeline.ImpactTimelineResolver;

@ApplicationScoped
public class ImpactConfig
{
    @Singleton
    public ImpactTimelineResolver provideImpactTimelineResolver(
        KubernetesMetricRepository kubernetesRepository,
        LLMModelMetricRepository llmRepository
    ) {
        return new ImpactTimelineResolver(kubernetesRepository, llmRepository);
    }
}
