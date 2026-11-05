package tech.illuin.wombat.endpoint;

import tech.illuin.wombat.engine.impact.timeline.ImpactTimeline;

import java.util.List;

public record TimelineResponse(
    long stepMs,
    String stepLabel,
    String stepPlural,
    List<Long> bucketStarts,
    List<ImpactTimeline.Series> series
)
{
    public static TimelineResponse from(ImpactTimeline timeline)
    {
        return new TimelineResponse(
            timeline.stepMs(),
            timeline.stepLabel(),
            timeline.stepPlural(),
            timeline.bucketStarts(),
            timeline.series()
        );
    }
}
