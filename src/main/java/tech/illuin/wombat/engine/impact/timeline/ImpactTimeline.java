package tech.illuin.wombat.engine.impact.timeline;

import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.evaluation.impact.commons.AssetImpact;
import tech.illuin.wombat.core.evaluation.impact.commons.Footprint;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static tech.illuin.wombat.core.activity.commons.TimeRange.toEpochMs;

public record ImpactTimeline(
    TimelineStep step,
    List<Long> bucketStarts,
    List<Series> series
) {
    private static final List<Metric> METRICS = List.of(
        new Metric("gwp", "GWP", "kgCO2eq", Footprint::gwp),
        new Metric("pe", "Primary energy", "MJ", Footprint::pe),
        new Metric("adp", "Abiotic resources", "kgSbeq", Footprint::adp)
    );

    public static ImpactTimeline of(TimeRange range, List<AssetImpact> impacts)
    {
        TimelineStep step = TimelineStep.of(range.resolution());
        List<Long> starts = bucketStarts(range, range.resolution().toMillis());
        if (starts.isEmpty())
            return new ImpactTimeline(step, List.of(), List.of());

        List<Series> series = METRICS.stream()
            .map(metric -> collect(metric, starts, range.resolution().toMillis(), impacts))
            .toList();
        return new ImpactTimeline(step, starts, series);
    }

    /**
     * Each impact was evaluated over a single bucket of the range: its footprint is added as-is to the
     * bucket its own range starts in.
     */
    private static Series collect(Metric metric, List<Long> starts, long stepMs, List<AssetImpact> impacts)
    {
        double[] embedded = new double[starts.size()];
        double[] use = new double[starts.size()];
        String unit = null;

        for (AssetImpact impact : impacts)
        {
            if (impact.footprint() == null || impact.range() == null)
                continue;

            int index = bucketIndex(starts.getFirst(), stepMs, starts.size(), toEpochMs(impact.range().start()));
            if (index < 0)
                continue;

            Footprint.FootprintImpact component = metric.component().apply(impact.footprint());
            if (component == null)
                continue;
            if (unit == null)
                unit = component.unit();

            if (component.embedded() != null)
                embedded[index] += component.embedded().value();
            if (component.use() != null)
                use[index] += component.use().value();
        }

        List<Bucket> buckets = new ArrayList<>(starts.size());
        for (int i = 0; i < starts.size(); i++)
            buckets.add(new Bucket(embedded[i], use[i]));
        return new Series(metric.key(), metric.label(), unit == null ? metric.defaultUnit() : unit, buckets);
    }

    private static int bucketIndex(long firstStart, long stepMs, int bucketCount, long start)
    {
        if (start < firstStart)
            return -1;
        long index = (start - firstStart) / stepMs;
        return index < bucketCount ? (int) index : -1;
    }

    /**
     * The buckets are laid from the start of the range, as {@link TimeRange#split()} does when the
     * evaluation is resolved, so that each impact's range matches a bucket.
     */
    private static List<Long> bucketStarts(TimeRange range, long stepMs)
    {
        long start = toEpochMs(range.start());
        long end = toEpochMs(range.end());
        if (stepMs <= 0 || start == Long.MIN_VALUE || end == Long.MAX_VALUE || end <= start)
            return List.of();

        List<Long> starts = new ArrayList<>();
        for (long bucket = start; bucket < end; bucket += stepMs)
            starts.add(bucket);
        return starts;
    }

    public boolean empty()
    {
        return this.bucketStarts.isEmpty();
    }

    public long stepMs()
    {
        return this.step.millis();
    }

    public String stepLabel()
    {
        return this.step.label();
    }

    public String stepPlural()
    {
        return this.step.plural();
    }

    public int bucketCount()
    {
        return this.bucketStarts.size();
    }

    public record Series(
        String key,
        String label,
        String unit,
        List<Bucket> buckets
    ) {}

    public record Bucket(
        double embedded,
        double use
    ) {
        public double total()
        {
            return this.embedded + this.use;
        }
    }

    private record Metric(
        String key,
        String label,
        String defaultUnit,
        Function<Footprint, Footprint.FootprintImpact> component
    ) {}
}
