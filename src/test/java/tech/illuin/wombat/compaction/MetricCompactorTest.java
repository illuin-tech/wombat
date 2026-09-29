package tech.illuin.wombat.compaction;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.core.source.data.LLMData;
import tech.illuin.wombat.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.impact.llm.LLMModelMetricRepository;
import tech.illuin.wombat.persistence.micrometer.MetricsConfig;
import tech.illuin.wombat.persistence.model.KubernetesMetricEntity;
import tech.illuin.wombat.persistence.model.LLMMetricEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which windows a run folds, and which it leaves alone: the one still being collected, and the one
 * that just closed but whose tail the meter registry may not have published yet.
 */
@QuarkusTest
class MetricCompactorTest
{
    private static final long HOUR_MS = MetricsConfig.COMPACTION_MILLIS;
    private static final long FIVE_MINUTES_MS = MetricsConfig.SAMPLING.toMillis();

    @Inject MetricCompactor compactor;
    @Inject KubernetesMetricRepository kubernetesRepository;
    @Inject LLMModelMetricRepository llmRepository;

    @BeforeEach
    @Transactional
    void clean()
    {
        kubernetesRepository.deleteAll();
        llmRepository.deleteAll();
    }

    @Test
    void compact_foldsEveryClosedWindowAndLeavesTheCurrentOneCollecting()
    {
        this.seedContainerHour(0L, 2.0);
        this.seedContainerHour(HOUR_MS, 6.0);
        this.seedContainerHour(2 * HOUR_MS, 9.0);

        // 15 minutes into the third hour: the first two are closed and past the publication delay.
        MetricCompactor.CompactionReport report = compactor.compact(instant(2 * HOUR_MS + Duration.ofMinutes(15).toMillis()));

        assertEquals(2, report.containerWindows());
        assertEquals(4, report.containerRows(), "one row per container per window");
        // Each hour holds api at cpu and worker at cpu/2, and the average sums the containers of an
        // instant. The serving range is inclusive on both ends, so each hour is asked for on its own.
        assertEquals(3.0, kubernetesRepository.averageCpuPerInstant(0L, HOUR_MS - 1, List.of("c1")).orElseThrow(), 1e-5);
        assertEquals(9.0, kubernetesRepository.averageCpuPerInstant(HOUR_MS, 2 * HOUR_MS - 1, List.of("c1")).orElseThrow(), 1e-5);
        assertTrue(kubernetesRepository.averageCpuPerInstant(2 * HOUR_MS, 3 * HOUR_MS, List.of("c1")).isEmpty(),
            "the hour still being collected is not served");

        List<KubernetesMetricEntity> current = kubernetesRepository.findByRange(2 * HOUR_MS, 3 * HOUR_MS);
        assertEquals(4, current.size(), "its sampling rows are left untouched");
        assertTrue(current.stream().noneMatch(row -> row.compacted));
    }

    /**
     * A window is folded as soon as it closes, without waiting out the registry's publication jitter:
     * a sample that lands afterwards is merged into the hour by the next run rather than adding a
     * second row for it.
     */
    @Test
    void compact_mergesSamplesThatLandAfterTheirWindowWasFolded()
    {
        kubernetesRepository.save(containerRow(0L, "api", 2.0));
        compactor.compact(instant(HOUR_MS + Duration.ofMinutes(1).toMillis()));

        kubernetesRepository.save(containerRow(11 * FIVE_MINUTES_MS, "api", 8.0));
        MetricCompactor.CompactionReport report = compactor.compact(instant(2 * HOUR_MS + Duration.ofMinutes(1).toMillis()));

        assertEquals(1, report.containerWindows());
        assertEquals(1, kubernetesRepository.findByRange(0L, HOUR_MS - 1).size(), "one row for the hour, not two");
        assertEquals(5.0, kubernetesRepository.averageCpuPerInstant(0L, HOUR_MS - 1, List.of("c1")).orElseThrow(), 1e-5);
    }

    /** A backlog — downtime, a stopped scheduler, rows older than compaction itself — is worked through. */
    @Test
    void compact_foldsABacklogOfWindows()
    {
        for (int hour = 0; hour < 6; hour++)
            this.seedContainerHour(hour * HOUR_MS, hour + 1.0);

        MetricCompactor.CompactionReport report = compactor.compact(instant(10 * HOUR_MS));

        assertEquals(6, report.containerWindows());
        assertEquals(12, kubernetesRepository.findByRange(0L, 6 * HOUR_MS).size(), "two containers per window");
        // Hourly sums of 1.5 × (1..6), averaged over the six windows.
        assertEquals(5.25, kubernetesRepository.averageCpuPerInstant(0L, 6 * HOUR_MS, List.of("c1")).orElseThrow(), 1e-5);
    }

    @Test
    void compact_foldsModelRowsToo()
    {
        llmRepository.save(llmRow(0L, 10L));
        llmRepository.save(llmRow(1000L, 20L));

        MetricCompactor.CompactionReport report = compactor.compact(instant(2 * HOUR_MS));

        assertEquals(1, report.modelWindows());
        assertEquals(1, report.modelRows());
        assertEquals(30L, llmRepository.sumOutputTokens(0L, HOUR_MS, "p-llm"));
    }

    @Test
    void compact_withNothingToFold_reportsNothing()
    {
        MetricCompactor.CompactionReport report = compactor.compact(instant(5 * HOUR_MS));

        assertEquals(MetricCompactor.CompactionReport.empty(), report);
    }

    /** Two containers sampled twice in the hour, so a fold has something to average and to group. */
    private void seedContainerHour(long hourStartMs, double cpu)
    {
        kubernetesRepository.save(containerRow(hourStartMs, "api", cpu));
        kubernetesRepository.save(containerRow(hourStartMs + FIVE_MINUTES_MS, "api", cpu));
        kubernetesRepository.save(containerRow(hourStartMs, "worker", cpu / 2));
        kubernetesRepository.save(containerRow(hourStartMs + FIVE_MINUTES_MS, "worker", cpu / 2));
    }

    private static KubernetesMetricEntity containerRow(long instantMs, String container, double cpu)
    {
        KubernetesMetricEntity row = new KubernetesMetricEntity();
        row.instantMs = instantMs;
        row.windowMs = FIVE_MINUTES_MS;
        row.assign(new AssetIdentity("c1", "env", "Cluster One"));
        row.assetType = "tech.illuin.wombat-module.kubernetes-api";
        row.data = new KubernetesData(container, "c1", "ns", "pod", cpu, 0.0);
        row.cpuNanocores = cpu;
        return row;
    }

    private static LLMMetricEntity llmRow(long instantMs, long tokens)
    {
        LLMMetricEntity row = new LLMMetricEntity();
        row.instantMs = instantMs;
        row.assign(new AssetIdentity("p-llm", "env", "Prometheus LLM"));
        row.assetType = "tech.illuin.wombat-module.llm-prometheus";
        row.data = new LLMData("m", LLMProvider.mistralai, "m", "FRA", tokens);
        row.outputTokens = tokens;
        return row;
    }

    private static Instant instant(long epochMs)
    {
        return Instant.ofEpochMilli(epochMs);
    }
}
