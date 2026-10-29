package tech.illuin.wombat.engine.impact.timeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.activity.WombatActivityException;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.evaluation.impact.commons.AssetImpact;
import tech.illuin.wombat.core.evaluation.impact.commons.ServiceImpact;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ImpactTimelineResolver
{
    private final AssetEvaluator assetEvaluator;

    private static final Logger logger = LoggerFactory.getLogger(ImpactTimelineResolver.class);

    public ImpactTimelineResolver(AssetEvaluator assetEvaluator)
    {
        this.assetEvaluator = assetEvaluator;
    }

    public ImpactTimeline resolve(TimeRange range, List<AssetImpact> impacts, Collection<String> includedServices)
    {
        TimelineStep step = TimelineStep.of(range);
        Map<String, Map<String, Map<Long, Double>>> weights = new LinkedHashMap<>();
        for (AssetImpact impact : impacts)
        {
            List<String> services = impact.serviceImpacts().stream()
                .map(ServiceImpact::serviceId)
                .filter(includedServices::contains)
                .toList();
            if (services.isEmpty())
                continue;
            weights.put(impact.assetIdentity().id(), this.activityOf(impact, services, range, step));
        }
        return ImpactTimeline.of(range, step, impacts, includedServices, weights);
    }

    private Map<String, Map<Long, Double>> activityOf(AssetImpact impact, List<String> services, TimeRange range, TimelineStep step)
    {
        try {
            return this.assetEvaluator.activityPerBucket(impact, range, step.millis(), services).orElse(Map.of());
        }
        catch (WombatActivityException | RuntimeException e) {
            logger.warn("Failed to resolve the per-bucket activity of asset {}, its impact is spread evenly", impact.assetIdentity().id(), e);
            return Map.of();
        }
    }
}
