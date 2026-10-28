package tech.illuin.wombat.persistence.compaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.engine.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.engine.impact.llm.LLMModelMetricRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static tech.illuin.wombat.persistence.micrometer.MetricsConfig.SAMPLING;

public class MetricCompactor
{
    private final KubernetesMetricRepository kubernetesRepository;
    private final LLMModelMetricRepository llmRepository;
    private final long windowMs;
    private final Duration delay;

    private static final long HOUR_MS = Duration.ofHours(1).toMillis();
    private static final Logger logger = LoggerFactory.getLogger(MetricCompactor.class);

    public MetricCompactor(KubernetesMetricRepository kubernetesRepository, LLMModelMetricRepository llmRepository, Duration window, Duration delay)
    {
        long windowMs = window.toMillis();
        if (windowMs <= 0 || windowMs % SAMPLING.toMillis() != 0 || HOUR_MS % windowMs != 0)
            throw new IllegalArgumentException("Compaction window must be a multiple of the " + SAMPLING + " sampling step that divides an hour, got: " + window);
        if (delay.isNegative())
            throw new IllegalArgumentException("Compaction delay must not be negative, got: " + delay);

        this.kubernetesRepository = kubernetesRepository;
        this.llmRepository = llmRepository;
        this.windowMs = windowMs;
        this.delay = delay;
    }

    public CompactionReport compact(Instant now)
    {
        long stepMs = this.windowMs;
        long cutoffMs = now.minus(this.delay).toEpochMilli();
        long lastClosedWindowMs = cutoffMs - Math.floorMod(cutoffMs, stepMs);

        List<Long> containerBuckets = this.kubernetesRepository.uncompactedBuckets(stepMs, lastClosedWindowMs);
        List<Long> modelBuckets = this.llmRepository.uncompactedBuckets(stepMs, lastClosedWindowMs);
        if (containerBuckets.isEmpty() && modelBuckets.isEmpty())
        {
            logger.debug("Nothing to compact before {}", Instant.ofEpochMilli(lastClosedWindowMs));
            return CompactionReport.empty();
        }

        int containerRows = 0;
        for (Long bucket : containerBuckets)
            containerRows += this.kubernetesRepository.compactBucket(bucket, stepMs);

        int modelRows = 0;
        for (Long bucket : modelBuckets)
            modelRows += this.llmRepository.compactBucket(bucket, stepMs);

        CompactionReport report = new CompactionReport(containerBuckets.size(), containerRows, modelBuckets.size(), modelRows);
        logger.info(
            "Compacted {} container window(s) into {} row(s) and {} model window(s) into {} row(s), up to {}",
            report.containerWindows(), report.containerRows(), report.modelWindows(), report.modelRows(),
            Instant.ofEpochMilli(lastClosedWindowMs)
        );
        return report;
    }

    public record CompactionReport(
        int containerWindows,
        int containerRows,
        int modelWindows,
        int modelRows
    ) {
        public static CompactionReport empty()
        {
            return new CompactionReport(0, 0, 0, 0);
        }
    }
}
