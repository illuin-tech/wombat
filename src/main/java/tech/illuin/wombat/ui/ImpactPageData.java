package tech.illuin.wombat.ui;

import tech.illuin.wombat.core.evaluation.impact.commons.Footprint;
import tech.illuin.wombat.engine.impact.timeline.ImpactTimeline;

import java.util.List;

public record ImpactPageData(List<Service> services, Timeline timeline)
{
    public static ImpactPageData of(EnvironmentImpact environment, ImpactTimeline timeline)
    {
        return new ImpactPageData(
            environment.serviceImpacts().stream().map(service -> new Service(
                service.service(),
                service.assetName(),
                environment.impactShares().getOrDefault(service.label(), 0.0),
                Metric.of(service.footprint().gwp()),
                Metric.of(service.footprint().pe()),
                Metric.of(service.footprint().adp())
            )).toList(),
            new Timeline(timeline.bucketStarts(), timeline.stepMs(), timeline.series())
        );
    }

    public record Service(String name, String asset, double share, Metric gwp, Metric pe, Metric adp) {}

    public record Metric(double embedded, double use)
    {
        static Metric of(Footprint.FootprintImpact impact)
        {
            return new Metric(impact.embedded().value(), impact.use().value());
        }
    }

    public record Timeline(List<Long> starts, long stepMs, List<ImpactTimeline.Series> series) {}
}
