package tech.illuin.wombat.impact.timeline;

import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.evaluation.impact.commons.AssetImpact;
import tech.illuin.wombat.core.evaluation.impact.commons.Footprint;
import tech.illuin.wombat.core.evaluation.impact.commons.ServiceImpact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    public static ImpactTimeline of(
        TimeRange range,
        TimelineStep step,
        List<AssetImpact> impacts,
        Collection<String> includedServices,
        Map<String, Map<Long, Double>> weightsByAsset
    ) {
        List<Long> starts = bucketStarts(range, step.millis());
        if (starts.isEmpty())
            return new ImpactTimeline(step, List.of(), List.of());

        List<Series> series = METRICS.stream()
            .map(metric -> spread(metric, starts.size(), impacts, includedServices, orderedWeights(starts, weightsByAsset)))
            .toList();
        return new ImpactTimeline(step, starts, series);
    }

    private static Map<String, double[]> orderedWeights(List<Long> starts, Map<String, Map<Long, Double>> weightsByAsset)
    {
        Map<String, double[]> ordered = new java.util.LinkedHashMap<>();
        weightsByAsset.forEach((assetId, weights) -> {
            double[] perBucket = new double[starts.size()];
            for (int i = 0; i < starts.size(); i++)
                perBucket[i] = Math.max(0.0, weights.getOrDefault(starts.get(i), 0.0));
            ordered.put(assetId, perBucket);
        });
        return ordered;
    }

    private static Series spread(
        Metric metric,
        int bucketCount,
        List<AssetImpact> impacts,
        Collection<String> includedServices,
        Map<String, double[]> weightsByAsset
    ) {
        double[] embedded = new double[bucketCount];
        double[] use = new double[bucketCount];
        String unit = null;

        for (AssetImpact impact : impacts)
        {
            List<Footprint> included = impact.serviceImpacts().stream()
                .filter(service -> includedServices.contains(service.serviceId()))
                .map(ServiceImpact::footprint)
                .toList();
            if (included.isEmpty())
                continue;

            Footprint.FootprintImpact component = metric.component().apply(Footprint.sum(included));
            if (component == null)
                continue;
            if (unit == null)
                unit = component.unit();

            spreadEvenly(embedded, component.embedded().value());
            spreadByActivity(use, component.use().value(), weightsByAsset.get(impact.assetId()));
        }

        List<Bucket> buckets = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; i++)
            buckets.add(new Bucket(embedded[i], use[i]));
        return new Series(metric.key(), metric.label(), unit == null ? metric.defaultUnit() : unit, buckets);
    }

    private static List<Long> bucketStarts(TimeRange range, long stepMs)
    {
        long start = toEpochMs(range.start());
        long end = toEpochMs(range.end());
        if (stepMs <= 0 || start == Long.MIN_VALUE || end == Long.MAX_VALUE || end <= start)
            return List.of();

        List<Long> starts = new ArrayList<>();
        for (long bucket = start - Math.floorMod(start, stepMs); bucket < end; bucket += stepMs)
            starts.add(bucket);
        return starts;
    }

    private static void spreadEvenly(double[] target, double value)
    {
        double perBucket = value / target.length;
        for (int i = 0; i < target.length; i++)
            target[i] += perBucket;
    }

    private static void spreadByActivity(double[] target, double value, double[] weights)
    {
        double total = weights == null ? 0.0 : java.util.Arrays.stream(weights).sum();
        if (total <= 0)
        {
            spreadEvenly(target, value);
            return;
        }

        for (int i = 0; i < target.length; i++)
        {
            if (weights[i] > 0)
                target[i] += value * weights[i] / total;
        }
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
