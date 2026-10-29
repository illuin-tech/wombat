package tech.illuin.wombat.engine.impact.timeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.activity.WombatActivityException;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.evaluation.impact.commons.AssetImpact;
import tech.illuin.wombat.core.evaluation.impact.commons.ServiceImpact;
import tech.illuin.wombat.core.evaluation.impact.kubernetes.KubernetesImpact;
import tech.illuin.wombat.core.evaluation.impact.llm.LLMImpact;
import tech.illuin.wombat.engine.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.engine.impact.llm.LLMModelMetricRepository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static tech.illuin.wombat.core.activity.commons.TimeRange.toEpochMs;

public class ImpactTimelineResolver
{
    private final KubernetesMetricRepository kubernetesRepository;
    private final LLMModelMetricRepository llmRepository;
    private final AssetEvaluator assetEvaluator;

    private static final Logger logger = LoggerFactory.getLogger(ImpactTimelineResolver.class);

    public ImpactTimelineResolver(
        KubernetesMetricRepository kubernetesRepository,
        LLMModelMetricRepository llmRepository,
        AssetEvaluator assetEvaluator
    ) {
        this.kubernetesRepository = kubernetesRepository;
        this.llmRepository = llmRepository;
        this.assetEvaluator = assetEvaluator;
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
        try {
            Optional<Map<Long, Double>> external = this.assetEvaluator.activityPerBucket(impact, range, step.millis(), services);
            if (external.isPresent())
                return external.get();
        }
        catch (WombatActivityException | RuntimeException e) {
            logger.warn("Failed to resolve the per-bucket activity of asset {}, its impact is spread evenly", impact.assetId(), e);
            return Map.of();
        }

        long start = toEpochMs(range.start());
        long end = toEpochMs(range.end());

        if (impact.serviceImpacts().stream().anyMatch(KubernetesImpact.class::isInstance))
            return this.kubernetesRepository.cpuTimePerBucket(start, end, step.millis(), List.of(impact.assetId()), services);
        if (impact.serviceImpacts().stream().anyMatch(LLMImpact.class::isInstance))
            return this.llmRepository.outputTokensPerBucket(start, end, step.millis(), impact.assetId(), services);
        return Map.of();
    }
}
