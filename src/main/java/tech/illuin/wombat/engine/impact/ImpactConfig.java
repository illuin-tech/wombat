package tech.illuin.wombat.engine.impact;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.engine.impact.timeline.ImpactTimelineResolver;

@ApplicationScoped
public class ImpactConfig
{
    @Singleton
    public ImpactTimelineResolver provideImpactTimelineResolver(AssetEvaluator assetEvaluator)
    {
        return new ImpactTimelineResolver(assetEvaluator);
    }
}
