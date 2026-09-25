package tech.illuin.wombat.impact.timeline;

import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.evaluation.impact.commons.AssetImpact;
import tech.illuin.wombat.core.evaluation.impact.commons.ServiceImpact;
import tech.illuin.wombat.core.evaluation.impact.kubernetes.KubernetesImpact;
import tech.illuin.wombat.core.evaluation.impact.llm.LLMImpact;
import tech.illuin.wombat.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.impact.llm.LLMModelMetricRepository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static tech.illuin.wombat.core.activity.commons.TimeRange.toEpochMs;

public class ImpactTimelineResolver
{
    private final KubernetesMetricRepository kubernetesRepository;
    private final LLMModelMetricRepository llmRepository;

    public ImpactTimelineResolver(KubernetesMetricRepository kubernetesRepository, LLMModelMetricRepository llmRepository)
    {
        this.kubernetesRepository = kubernetesRepository;
        this.llmRepository = llmRepository;
    }

    public ImpactTimeline resolve(TimeRange range, List<AssetImpact> impacts, Collection<String> includedServices)
    {
        TimelineStep step = TimelineStep.of(range);
        Map<String, Map<Long, Double>> weights = new LinkedHashMap<>();
        for (AssetImpact impact : impacts)
        {
            List<String> services = impact.serviceImpacts().stream()
                .map(ServiceImpact::serviceId)
                .filter(includedServices::contains)
                .toList();
            if (services.isEmpty())
                continue;
            weights.put(impact.assetId(), this.activityOf(impact, services, range, step));
        }
        return ImpactTimeline.of(range, step, impacts, includedServices, weights);
    }

    private Map<Long, Double> activityOf(AssetImpact impact, List<String> services, TimeRange range, TimelineStep step)
    {
        long start = toEpochMs(range.start());
        long end = toEpochMs(range.end());

        if (impact.serviceImpacts().stream().anyMatch(KubernetesImpact.class::isInstance))
            return this.kubernetesRepository.cpuTimePerBucket(start, end, step.millis(), List.of(impact.assetId()), services);
        if (impact.serviceImpacts().stream().anyMatch(LLMImpact.class::isInstance))
            return this.llmRepository.outputTokensPerBucket(start, end, step.millis(), impact.assetId(), services);
        return Map.of();
    }
}
