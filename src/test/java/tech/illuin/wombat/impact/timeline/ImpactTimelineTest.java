package tech.illuin.wombat.impact.timeline;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.asset.AssetType;
import tech.illuin.wombat.core.asset.profile.Profile;
import tech.illuin.wombat.core.evaluation.impact.commons.AssetImpact;
import tech.illuin.wombat.core.evaluation.impact.commons.Footprint;
import tech.illuin.wombat.core.evaluation.impact.commons.ImpactProvider;
import tech.illuin.wombat.core.evaluation.impact.commons.ServiceImpact;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The distribution rules, away from any database: what each half of a footprint is spread by, that
 * the three metrics share the same spread, and that whatever the spread, every series still adds up
 * to the footprint the page reports.
 */
class ImpactTimelineTest
{
    private static final long HOUR_MS = 3_600_000L;
    private static final long DAY_MS = 24 * HOUR_MS;

    @Test
    void of_coversEveryBucketTheRangeTouches()
    {
        ImpactTimeline timeline = ImpactTimeline.of(
            range(3 * HOUR_MS + 1_800_000L, 7 * HOUR_MS), TimelineStep.HOUR, List.of(), Set.of(), Map.of());

        assertEquals(4, timeline.bucketCount());
        assertEquals(3 * HOUR_MS, timeline.bucketStarts().getFirst());
        assertEquals(6 * HOUR_MS, timeline.bucketStarts().getLast());
    }

    @Test
    void of_offersTheThreeMetricsInOrder()
    {
        ImpactTimeline timeline = ImpactTimeline.of(
            range(0, HOUR_MS), TimelineStep.HOUR, List.of(), Set.of(), Map.of());

        assertEquals(List.of("gwp", "pe", "adp"), timeline.series().stream().map(ImpactTimeline.Series::key).toList());
    }

    /** Manufacturing accrues with time passing, the use phase with the work actually measured. */
    @Test
    void of_spreadsEmbeddedEvenlyAndUseByMeasuredActivity()
    {
        ImpactTimeline timeline = ImpactTimeline.of(
            range(0, 4 * HOUR_MS),
            TimelineStep.HOUR,
            List.of(impact("cluster", service("api", 12.0f, 8.0f))),
            Set.of("api"),
            Map.of("cluster", Map.of(HOUR_MS, 1.0, 2 * HOUR_MS, 3.0))
        );

        List<ImpactTimeline.Bucket> buckets = series(timeline, "gwp").buckets();
        assertEquals(4, buckets.size());
        buckets.forEach(bucket -> assertEquals(3.0, bucket.embedded(), 1e-6));
        assertEquals(0.0, buckets.get(0).use(), 1e-6);
        assertEquals(2.0, buckets.get(1).use(), 1e-6);
        assertEquals(6.0, buckets.get(2).use(), 1e-6);
        assertEquals(0.0, buckets.get(3).use(), 1e-6);
    }

    /** The work done in an hour does not depend on which impact is read off it. */
    @Test
    void of_spreadsEveryMetricOverTheSameActivity()
    {
        ImpactTimeline timeline = ImpactTimeline.of(
            range(0, 2 * HOUR_MS),
            TimelineStep.HOUR,
            List.of(impact("cluster", service("api", 12.0f, 8.0f, 30.0f, 10.0f, 0.6f, 0.4f))),
            Set.of("api"),
            Map.of("cluster", Map.of(HOUR_MS, 1.0))
        );

        // Every metric puts its whole use in the only bucket with activity, and half its embedded in each.
        assertUseOnlyInSecondBucket(series(timeline, "gwp"), 6.0, 8.0);
        assertUseOnlyInSecondBucket(series(timeline, "pe"), 15.0, 10.0);
        assertUseOnlyInSecondBucket(series(timeline, "adp"), 0.3, 0.4);
    }

    @Test
    void of_carriesTheUnitOfEachMetric()
    {
        ImpactTimeline timeline = ImpactTimeline.of(
            range(0, HOUR_MS),
            TimelineStep.HOUR,
            List.of(impact("cluster", service("api", 12.0f, 8.0f, 30.0f, 10.0f, 0.6f, 0.4f))),
            Set.of("api"),
            Map.of()
        );

        assertEquals("kgCO2eq", series(timeline, "gwp").unit());
        assertEquals("MJ", series(timeline, "pe").unit());
        assertEquals("kgSbeq", series(timeline, "adp").unit());
    }

    /** A modelled asset measures nothing; a constant rate over the range is the only honest split. */
    @Test
    void of_withoutMeasuredActivity_spreadsUseEvenlyToo()
    {
        ImpactTimeline timeline = ImpactTimeline.of(
            range(0, 4 * HOUR_MS),
            TimelineStep.HOUR,
            List.of(impact("model", service("mistral", 4.0f, 20.0f))),
            Set.of("mistral"),
            Map.of()
        );

        series(timeline, "gwp").buckets().forEach(bucket -> {
            assertEquals(1.0, bucket.embedded(), 1e-6);
            assertEquals(5.0, bucket.use(), 1e-6);
        });
    }

    @Test
    void of_bucketsAddUpToTheReportedFootprint()
    {
        ImpactTimeline timeline = ImpactTimeline.of(
            range(0, 5 * HOUR_MS),
            TimelineStep.HOUR,
            List.of(
                impact("cluster", service("api", 12.0f, 8.0f), service("worker", 3.0f, 1.0f)),
                impact("model", service("mistral", 4.0f, 20.0f))
            ),
            Set.of("api", "worker", "mistral"),
            Map.of("cluster", Map.of(0L, 2.0, 3 * HOUR_MS, 1.0))
        );

        double total = series(timeline, "gwp").buckets().stream().mapToDouble(ImpactTimeline.Bucket::total).sum();
        assertEquals(12.0 + 8.0 + 3.0 + 1.0 + 4.0 + 20.0, total, 1e-5);
    }

    @Test
    void of_countsOnlyTheSelectedServices()
    {
        ImpactTimeline timeline = ImpactTimeline.of(
            range(0, 2 * HOUR_MS),
            TimelineStep.HOUR,
            List.of(impact("cluster", service("api", 10.0f, 0.0f), service("worker", 90.0f, 0.0f))),
            Set.of("api"),
            Map.of()
        );

        double total = series(timeline, "gwp").buckets().stream().mapToDouble(ImpactTimeline.Bucket::total).sum();
        assertEquals(10.0, total, 1e-5);
    }

    /**
     * A day is a whole number of hours, so a daily step is the same alignment one rung up: the
     * columns land on UTC midnight and hold exactly the hours of their day.
     */
    @Test
    void of_withADailyStep_alignsBucketsOnUtcDays()
    {
        ImpactTimeline timeline = ImpactTimeline.of(
            range(DAY_MS + 5 * HOUR_MS, 3 * DAY_MS),
            TimelineStep.DAY,
            List.of(impact("cluster", service("api", 12.0f, 9.0f))),
            Set.of("api"),
            Map.of("cluster", Map.of(DAY_MS, 1.0, 2 * DAY_MS, 2.0))
        );

        assertEquals(DAY_MS, timeline.stepMs());
        assertEquals("daily", timeline.stepLabel());
        assertEquals("days", timeline.stepPlural());
        assertEquals(List.of(DAY_MS, 2 * DAY_MS), timeline.bucketStarts());

        List<ImpactTimeline.Bucket> buckets = series(timeline, "gwp").buckets();
        buckets.forEach(bucket -> assertEquals(6.0, bucket.embedded(), 1e-6));
        assertEquals(3.0, buckets.get(0).use(), 1e-6);
        assertEquals(6.0, buckets.get(1).use(), 1e-6);
    }

    @Test
    void of_withAnOpenEndedRange_hasNoBuckets()
    {
        ImpactTimeline timeline = ImpactTimeline.of(
            new TimeRange(Instant.MIN, Instant.MAX), TimelineStep.HOUR, List.of(), Set.of(), Map.of());

        assertTrue(timeline.empty());
        assertTrue(timeline.series().isEmpty());
    }

    private static void assertUseOnlyInSecondBucket(ImpactTimeline.Series series, double embeddedPerBucket, double use)
    {
        assertEquals(embeddedPerBucket, series.buckets().get(0).embedded(), 1e-6);
        assertEquals(0.0, series.buckets().get(0).use(), 1e-6);
        assertEquals(embeddedPerBucket, series.buckets().get(1).embedded(), 1e-6);
        assertEquals(use, series.buckets().get(1).use(), 1e-6);
    }

    private static ImpactTimeline.Series series(ImpactTimeline timeline, String key)
    {
        return timeline.series().stream()
            .filter(series -> series.key().equals(key))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no series " + key + " in " + timeline.series()));
    }

    private static TimeRange range(long startMs, long endMs)
    {
        return new TimeRange(Instant.ofEpochMilli(startMs), Instant.ofEpochMilli(endMs));
    }

    private static AssetImpact impact(String assetId, ServiceImpact... services)
    {
        List<ServiceImpact> serviceImpacts = List.of(services);
        Footprint footprint = Footprint.sum(serviceImpacts.stream().map(ServiceImpact::footprint).toList());
        return new AssetImpact("env", assetId, footprint, serviceImpacts, ImpactProvider.BOAVIZTA);
    }

    private static ServiceImpact service(String serviceId, float embedded, float use)
    {
        return new TestServiceImpact(serviceId, new Footprint(component("kgCO2eq", embedded, use), null, null));
    }

    private static ServiceImpact service(
        String serviceId,
        float gwpEmbedded, float gwpUse,
        float peEmbedded, float peUse,
        float adpEmbedded, float adpUse
    ) {
        return new TestServiceImpact(serviceId, new Footprint(
            component("kgCO2eq", gwpEmbedded, gwpUse),
            component("MJ", peEmbedded, peUse),
            component("kgSbeq", adpEmbedded, adpUse)
        ));
    }

    private static Footprint.FootprintImpact component(String unit, float embedded, float use)
    {
        return new Footprint.FootprintImpact(
            unit, "",
            new Footprint.FootprintImpact.FootprintImpactItem(embedded, List.of()),
            new Footprint.FootprintImpact.FootprintImpactItem(use, List.of())
        );
    }

    /** Only the service id and the footprint matter here; the rest of the contract stays unused. */
    private record TestServiceImpact(String serviceId, Footprint footprint) implements ServiceImpact
    {
        @Override
        public double share()
        {
            return 0.0;
        }

        @Override
        public Profile profile()
        {
            return null;
        }

        @Override
        public AssetType assetType()
        {
            return AssetType.KUBERNETES_API;
        }
    }
}
