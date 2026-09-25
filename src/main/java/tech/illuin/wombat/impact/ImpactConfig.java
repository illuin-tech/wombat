package tech.illuin.wombat.impact;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import tech.illuin.wombat.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.impact.llm.LLMModelMetricRepository;
import tech.illuin.wombat.impact.timeline.ImpactTimelineResolver;

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
