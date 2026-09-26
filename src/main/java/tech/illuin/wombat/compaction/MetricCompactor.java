package tech.illuin.wombat.compaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.impact.llm.LLMModelMetricRepository;

import java.time.Instant;
import java.util.List;

import static tech.illuin.wombat.persistence.micrometer.MetricsConfig.COMPACTION_DELAY;
import static tech.illuin.wombat.persistence.micrometer.MetricsConfig.COMPACTION_MILLIS;

public class MetricCompactor
{
    private final KubernetesMetricRepository kubernetesRepository;
    private final LLMModelMetricRepository llmRepository;

    private static final Logger logger = LoggerFactory.getLogger(MetricCompactor.class);

    public MetricCompactor(KubernetesMetricRepository kubernetesRepository, LLMModelMetricRepository llmRepository)
    {
        this.kubernetesRepository = kubernetesRepository;
        this.llmRepository = llmRepository;
    }

    public CompactionReport compact(Instant now)
    {
        long stepMs = COMPACTION_MILLIS;
        long cutoffMs = now.minus(COMPACTION_DELAY).toEpochMilli();
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
