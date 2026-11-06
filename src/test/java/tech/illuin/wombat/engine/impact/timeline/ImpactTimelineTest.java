package tech.illuin.wombat.engine.impact.timeline;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.evaluation.impact.commons.AssetImpact;
import tech.illuin.wombat.core.evaluation.impact.commons.Footprint;
import tech.illuin.wombat.core.evaluation.impact.commons.ImpactProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The timeline is laid over the resolution of its range, and each bucket holds the footprints the
 * evaluator reported for that bucket, as they were reported.
 */
class ImpactTimelineTest
{
    private static final long HOUR_MS = 3_600_000L;
    private static final long DAY_MS = 24 * HOUR_MS;

    @Test
    void of_laysOneBucketPerResolutionFromTheRangeStart()
    {
        ImpactTimeline timeline = ImpactTimeline.of(range(3 * HOUR_MS + 1_800_000L, 7 * HOUR_MS + 1_800_000L, HOUR_MS), List.of());

        assertEquals(4, timeline.bucketCount());
        assertEquals(3 * HOUR_MS + 1_800_000L, timeline.bucketStarts().getFirst());
        assertEquals(6 * HOUR_MS + 1_800_000L, timeline.bucketStarts().getLast());
        assertEquals(HOUR_MS, timeline.stepMs());
        assertEquals("hourly", timeline.stepLabel());
    }

    @Test
    void of_offersTheThreeMetricsInOrder()
    {
        ImpactTimeline timeline = ImpactTimeline.of(range(0, HOUR_MS, HOUR_MS), List.of());

        assertEquals(List.of("gwp", "pe", "adp"), timeline.series().stream().map(ImpactTimeline.Series::key).toList());
    }

    @Test
    void of_putsEachFootprintInTheBucketOfItsRange()
    {
        ImpactTimeline timeline = ImpactTimeline.of(
            range(0, 3 * HOUR_MS, HOUR_MS),
            List.of(
                impact("cluster", HOUR_MS, footprint(2.0f, 5.0f)),
                impact("cluster", 2 * HOUR_MS, footprint(1.0f, 3.0f))
            )
        );

        List<ImpactTimeline.Bucket> buckets = series(timeline, "gwp").buckets();
        assertEquals(3, buckets.size());
        assertEquals(0.0, buckets.get(0).total(), 1e-6);
        assertEquals(2.0, buckets.get(1).embedded(), 1e-6);
        assertEquals(5.0, buckets.get(1).use(), 1e-6);
        assertEquals(1.0, buckets.get(2).embedded(), 1e-6);
        assertEquals(3.0, buckets.get(2).use(), 1e-6);
    }

    @Test
    void of_addsUpTheAssetsOfASameBucket()
    {
        ImpactTimeline timeline = ImpactTimeline.of(
            range(0, 2 * HOUR_MS, HOUR_MS),
            List.of(
                impact("cluster", 0, footprint(2.0f, 5.0f)),
                impact("model", 0, footprint(1.0f, 4.0f))
            )
        );

        ImpactTimeline.Bucket bucket = series(timeline, "gwp").buckets().getFirst();
        assertEquals(3.0, bucket.embedded(), 1e-6);
        assertEquals(9.0, bucket.use(), 1e-6);
    }

    @Test
    void of_readsEachMetricOffTheReportedFootprint()
    {
        ImpactTimeline timeline = ImpactTimeline.of(
            range(0, HOUR_MS, HOUR_MS),
            List.of(impact("cluster", 0, new Footprint(
                component("kgCO2eq", 12.0f, 8.0f),
                component("MJ", 30.0f, 10.0f),
                component("kgSbeq", 0.6f, 0.4f)
            )))
        );

        assertBucket(series(timeline, "gwp"), 12.0, 8.0);
        assertBucket(series(timeline, "pe"), 30.0, 10.0);
        assertBucket(series(timeline, "adp"), 0.6, 0.4);
        assertEquals("kgCO2eq", series(timeline, "gwp").unit());
        assertEquals("MJ", series(timeline, "pe").unit());
        assertEquals("kgSbeq", series(timeline, "adp").unit());
    }

    @Test
    void of_ignoresImpactsOutsideTheRange()
    {
        ImpactTimeline timeline = ImpactTimeline.of(
            range(HOUR_MS, 2 * HOUR_MS, HOUR_MS),
            List.of(
                impact("cluster", 0, footprint(2.0f, 5.0f)),
                impact("cluster", 2 * HOUR_MS, footprint(2.0f, 5.0f))
            )
        );

        assertEquals(0.0, series(timeline, "gwp").buckets().getFirst().total(), 1e-6);
    }

    @Test
    void of_withADailyResolution_laysDailyBuckets()
    {
        ImpactTimeline timeline = ImpactTimeline.of(
            range(DAY_MS, 3 * DAY_MS, DAY_MS),
            List.of(impact("cluster", 2 * DAY_MS, footprint(6.0f, 3.0f)))
        );

        assertEquals(DAY_MS, timeline.stepMs());
        assertEquals("daily", timeline.stepLabel());
        assertEquals("days", timeline.stepPlural());
        assertEquals(List.of(DAY_MS, 2 * DAY_MS), timeline.bucketStarts());
        assertEquals(0.0, series(timeline, "gwp").buckets().get(0).total(), 1e-6);
        assertEquals(9.0, series(timeline, "gwp").buckets().get(1).total(), 1e-6);
    }

    @Test
    void of_withAnOpenEndedRange_hasNoBuckets()
    {
        ImpactTimeline timeline = ImpactTimeline.of(new TimeRange(Instant.MIN, Instant.MAX, Duration.ofHours(1)), List.of());

        assertTrue(timeline.empty());
        assertTrue(timeline.series().isEmpty());
    }

    private static void assertBucket(ImpactTimeline.Series series, double embedded, double use)
    {
        assertEquals(embedded, series.buckets().getFirst().embedded(), 1e-6);
        assertEquals(use, series.buckets().getFirst().use(), 1e-6);
    }

    private static ImpactTimeline.Series series(ImpactTimeline timeline, String key)
    {
        return timeline.series().stream()
            .filter(series -> series.key().equals(key))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no series " + key + " in " + timeline.series()));
    }

    private static TimeRange range(long startMs, long endMs, long resolutionMs)
    {
        return new TimeRange(Instant.ofEpochMilli(startMs), Instant.ofEpochMilli(endMs), Duration.ofMillis(resolutionMs));
    }

    private static AssetImpact impact(String assetId, long bucketStartMs, Footprint footprint)
    {
        return new AssetImpact(
            AssetIdentity.of(assetId, "env", "name", "tech.illuin.wombat-module.kubernetes-api"),
            footprint,
            List.of(),
            ImpactProvider.BOAVIZTA,
            range(bucketStartMs, bucketStartMs + HOUR_MS, HOUR_MS)
        );
    }

    private static Footprint footprint(float embedded, float use)
    {
        return new Footprint(component("kgCO2eq", embedded, use), null, null);
    }

    private static Footprint.FootprintImpact component(String unit, float embedded, float use)
    {
        return new Footprint.FootprintImpact(
            unit, "",
            new Footprint.FootprintImpact.FootprintImpactItem(embedded, List.of()),
            new Footprint.FootprintImpact.FootprintImpactItem(use, List.of())
        );
    }
}
