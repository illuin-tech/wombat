package tech.illuin.wombat.persistence;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.persistence.model.KubernetesMetricEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class KubernetesMetricRepositoryTest
{
    private static final long FIVE_MINUTES_MS = 300_000L;
    private static final long HOUR_MS = 3_600_000L;

    @Inject
    KubernetesMetricRepository repository;

    @BeforeEach
    @Transactional
    void clean()
    {
        repository.deleteAll();
    }

    @Test
    void save_persistsEntity()
    {
        repository.save(row(1000L, "c1", "ns", "container-1"));

        List<KubernetesMetricEntity> result = repository.findByRange(0L, 5000L);
        assertEquals(1, result.size());
        assertEquals(new KubernetesData("container-1", "c1", "ns", "pod", 1.0, 0.0), result.getFirst().data);
    }

    @Test
    void findByRange_returnsOnlyWithinRange()
    {
        repository.save(row(100L, "c1", "ns", "a"));
        repository.save(row(200L, "c1", "ns", "b"));
        repository.save(row(300L, "c1", "ns", "c"));

        List<KubernetesMetricEntity> result = repository.findByRange(100L, 250L);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> "a".equals(source(e).serviceId())));
        assertTrue(result.stream().anyMatch(e -> "b".equals(source(e).serviceId())));
        assertFalse(result.stream().anyMatch(e -> "c".equals(source(e).serviceId())));
    }

    @Test
    void findByRangeAndAssets_emptyList_returnsAllInRange()
    {
        repository.save(row(100L, "c1", "ns", "a"));
        repository.save(row(200L, "c2", "ns", "b"));

        List<KubernetesMetricEntity> result = repository.findByRangeAndAssets(0L, 5000L, List.of());

        assertEquals(2, result.size());
    }

    @Test
    void findByRangeAndAssets_filtersByAsset()
    {
        repository.save(row(100L, "c1", "ns", "a"));
        repository.save(row(150L, "c2", "ns", "b"));
        repository.save(row(200L, "c3", "ns", "c"));

        List<KubernetesMetricEntity> result = repository.findByRangeAndAssets(0L, 5000L, List.of("c1", "c3"));

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> "a".equals(source(e).serviceId())));
        assertTrue(result.stream().anyMatch(e -> "c".equals(source(e).serviceId())));
        assertFalse(result.stream().anyMatch(e -> "b".equals(source(e).serviceId())));
    }

    @Test
    void findByRangeAndAssets_combinesRangeAndAssetFilters()
    {
        repository.save(row(100L, "c1", "ns", "in-range-c1"));
        repository.save(row(400L, "c1", "ns", "out-of-range-c1"));
        repository.save(row(200L, "c2", "ns", "in-range-c2"));

        List<KubernetesMetricEntity> result = repository.findByRangeAndAssets(0L, 300L, List.of("c1"));

        assertEquals(1, result.size());
        assertEquals("in-range-c1", source(result.getFirst()).serviceId());
    }

    /**
     * A bucket holds the CPU-time of its rows whatever window each covers: two five-minute rows and
     * one hourly row weigh by the span they stand for, not by being one row each.
     */
    @Test
    void cpuTimePerBucket_groupsRowsIntoBucketsWeightedByWindow()
    {
        repository.save(row(0L, FIVE_MINUTES_MS, "c1", "api", 2.0));
        repository.save(row(FIVE_MINUTES_MS, FIVE_MINUTES_MS, "c1", "api", 4.0));
        repository.save(row(2 * HOUR_MS, HOUR_MS, "c1", "api", 1.0));

        Map<Long, Double> perHour = repository.cpuTimePerBucket(0L, 3 * HOUR_MS, HOUR_MS, List.of("c1"), List.of());

        assertEquals(2, perHour.size());
        assertEquals(6.0 * FIVE_MINUTES_MS, perHour.get(0L), 1.0);
        assertEquals(1.0 * HOUR_MS, perHour.get(2 * HOUR_MS), 1.0);
    }

    @Test
    void cpuTimePerBucket_countsOnlyTheSelectedContainers()
    {
        repository.save(row(0L, HOUR_MS, "c1", "api", 2.0));
        repository.save(row(0L, HOUR_MS, "c1", "worker", 8.0));

        Map<Long, Double> perHour = repository.cpuTimePerBucket(0L, HOUR_MS, HOUR_MS, List.of("c1"), List.of("api"));

        assertEquals(2.0 * HOUR_MS, perHour.get(0L), 1.0);
    }

    /** A bucket no row covers is absent rather than zero: the caller knows the range it asked for. */
    @Test
    void cpuTimePerBucket_leavesBucketsWithoutRowsOut()
    {
        repository.save(row(0L, HOUR_MS, "c1", "api", 2.0));

        Map<Long, Double> perHour = repository.cpuTimePerBucket(0L, 5 * HOUR_MS, HOUR_MS, List.of("c1"), List.of());

        assertEquals(Set.of(0L), perHour.keySet());
    }

    /** An hour still being collected is a fraction of itself, so nothing serves it until it is folded. */
    @Test
    void servingQueries_ignoreRowsNotYetCompacted()
    {
        repository.save(sampled(0L, FIVE_MINUTES_MS, "c1", "api", 4.0));

        assertTrue(repository.averageCpuPerInstant(0L, HOUR_MS, List.of("c1")).isEmpty());
        assertTrue(repository.containerShares(0L, HOUR_MS, List.of("c1")).isEmpty());
        assertTrue(repository.cpuTimePerBucket(0L, HOUR_MS, HOUR_MS, List.of("c1"), List.of()).isEmpty());
        assertTrue(repository.containerLocations(0L, HOUR_MS, List.of("c1")).isEmpty());
    }

    /**
     * The fold replaces a window's sampling rows with one row per container, holding the mean each
     * row's own span weighs into — here two five-minute rows at 2.0 and one at 8.0 average to 4.0.
     */
    @Test
    void compactBucket_foldsTheWindowIntoOneRowPerContainer()
    {
        repository.save(withRam(sampled(0L, FIVE_MINUTES_MS, "c1", "api", 2.0), 20.0));
        repository.save(withRam(sampled(FIVE_MINUTES_MS, FIVE_MINUTES_MS, "c1", "api", 2.0), 20.0));
        repository.save(withRam(sampled(2 * FIVE_MINUTES_MS, FIVE_MINUTES_MS, "c1", "api", 8.0), 80.0));
        repository.save(sampled(0L, FIVE_MINUTES_MS, "c1", "worker", 1.0));

        int rows = repository.compactBucket(0L, HOUR_MS);

        assertEquals(2, rows);
        List<KubernetesMetricEntity> folded = repository.findByRange(0L, HOUR_MS);
        assertEquals(2, folded.size(), "the sampling rows should be gone");
        assertTrue(folded.stream().allMatch(row -> row.compacted));
        assertTrue(folded.stream().allMatch(row -> row.instantMs == 0L));
        // The window a folded row carries is the time it actually covers, not the nominal hour: api
        // was sampled three times, worker once.
        assertEquals(3 * FIVE_MINUTES_MS, folded.stream()
            .filter(row -> "api".equals(source(row).serviceId())).findFirst().orElseThrow().windowMs);
        assertEquals(FIVE_MINUTES_MS, folded.stream()
            .filter(row -> "worker".equals(source(row).serviceId())).findFirst().orElseThrow().windowMs);

        KubernetesMetricEntity api = folded.stream()
            .filter(row -> "api".equals(source(row).serviceId()))
            .findFirst().orElseThrow();
        assertEquals(4.0, api.cpuNanocores, 1e-6);
        assertEquals(40.0, api.ramBytes, 1e-6);
    }

    /**
     * The registry publishes a sampling window at a jittered offset that can land after the hour was
     * folded. The row that turns up then joins the folded one instead of becoming a second row for
     * the hour, which every reader would have counted twice.
     */
    @Test
    void compactBucket_mergesASampleThatArrivesAfterTheWindowWasFolded()
    {
        repository.save(sampled(0L, FIVE_MINUTES_MS, "c1", "api", 2.0));
        repository.save(sampled(FIVE_MINUTES_MS, FIVE_MINUTES_MS, "c1", "api", 2.0));
        repository.compactBucket(0L, HOUR_MS);

        repository.save(sampled(11 * FIVE_MINUTES_MS, FIVE_MINUTES_MS, "c1", "api", 8.0));
        assertEquals(1, repository.compactBucket(0L, HOUR_MS));

        List<KubernetesMetricEntity> folded = repository.findByRange(0L, HOUR_MS - 1);
        assertEquals(1, folded.size(), "one row for the hour, not two");
        // Ten minutes at 2.0 and five at 8.0, so the mean follows the time each stands for.
        assertEquals(4.0, folded.getFirst().cpuNanocores, 1e-6);
        assertEquals(3 * FIVE_MINUTES_MS, folded.getFirst().windowMs);
        assertEquals(4.0, repository.averageCpuPerInstant(0L, HOUR_MS - 1, List.of("c1")).orElseThrow(), 1e-5);
    }

    @Test
    void compactBucket_leavesTheOtherWindowsAlone()
    {
        repository.save(sampled(0L, FIVE_MINUTES_MS, "c1", "api", 2.0));
        repository.save(sampled(HOUR_MS, FIVE_MINUTES_MS, "c1", "api", 6.0));

        repository.compactBucket(0L, HOUR_MS);

        List<KubernetesMetricEntity> next = repository.findByRange(HOUR_MS, 2 * HOUR_MS);
        assertEquals(1, next.size());
        assertFalse(next.getFirst().compacted);
        assertEquals(6.0, next.getFirst().cpuNanocores, 1e-6);
    }

    /** Folding twice must not fold the folded row into itself. */
    @Test
    void compactBucket_isIdempotent()
    {
        repository.save(sampled(0L, FIVE_MINUTES_MS, "c1", "api", 2.0));
        repository.compactBucket(0L, HOUR_MS);

        assertEquals(0, repository.compactBucket(0L, HOUR_MS));
        assertEquals(1, repository.findByRange(0L, HOUR_MS).size());
        assertEquals(2.0, repository.averageCpuPerInstant(0L, HOUR_MS, List.of("c1")).orElseThrow(), 1e-5);
    }

    @Test
    void uncompactedBuckets_listsTheWindowsStillHoldingSamplingRowsBeforeTheCutoff()
    {
        repository.save(sampled(0L, FIVE_MINUTES_MS, "c1", "api", 1.0));
        repository.save(sampled(2 * HOUR_MS, FIVE_MINUTES_MS, "c1", "api", 1.0));
        repository.save(sampled(5 * HOUR_MS, FIVE_MINUTES_MS, "c1", "api", 1.0));
        repository.save(row(3 * HOUR_MS, HOUR_MS, "c1", "api", 1.0));

        assertEquals(List.of(0L, 2 * HOUR_MS), repository.uncompactedBuckets(HOUR_MS, 4 * HOUR_MS));
    }

    private static KubernetesMetricEntity row(long instantMs, String assetId, String namespace, String container)
    {
        return row(instantMs, FIVE_MINUTES_MS, assetId, container, 1.0);
    }

    /** A folded row, the kind every serving query reads. */
    private static KubernetesMetricEntity row(long instantMs, long windowMs, String assetId, String container, double cpu)
    {
        KubernetesMetricEntity row = sampled(instantMs, windowMs, assetId, container, cpu);
        row.compacted = true;
        return row;
    }

    @Test
    void uncompactedBuckets_collapsesSeveralRowsOfTheSameWindow()
    {
        repository.save(sampled(0L, FIVE_MINUTES_MS, "c1", "api", 1.0));
        repository.save(sampled(FIVE_MINUTES_MS, FIVE_MINUTES_MS, "c1", "api", 1.0));
        repository.save(sampled(FIVE_MINUTES_MS, FIVE_MINUTES_MS, "c1", "worker", 1.0));

        assertEquals(List.of(0L), repository.uncompactedBuckets(HOUR_MS, HOUR_MS));
    }

    private static KubernetesMetricEntity withRam(KubernetesMetricEntity row, double ramBytes)
    {
        KubernetesData data = source(row);
        row.ramBytes = ramBytes;
        row.data = new KubernetesData(
            data.serviceId(), data.cluster(), data.namespace(), data.pod(), data.cpuNanocores(), ramBytes);
        return row;
    }

    /** A sampling row as collection writes it, waiting to be folded. */
    private static KubernetesMetricEntity sampled(long instantMs, long windowMs, String assetId, String container, double cpu)
    {
        KubernetesMetricEntity row = new KubernetesMetricEntity();
        row.instantMs = instantMs;
        row.windowMs = windowMs;
        row.assign(identityOf(assetId));
        row.assetType = "tech.illuin.wombat-module.kubernetes-api";
        row.data = new KubernetesData(container, assetId, "ns", "pod", cpu, 0.0);
        row.cpuNanocores = cpu;
        return row;
    }

    /**
     * The Kubernetes source writes the asset id as the cluster, so every id these tests scope a read to is both.
     * The filters address the asset_id column, hence the identity rather than the payload.
     */
    private static AssetIdentity identityOf(String assetId)
    {
        return new AssetIdentity(assetId, "env", assetId + " name");
    }

    /**
     * Rows written under a five-minute window and rows written under an hourly one describe spans of
     * very different length; averaging them as equals would let the short ones dominate. Here the
     * plain average would be 4.0 against a time-weighted 8.71.
     */
    @Test
    void averageCpuPerInstant_weighsEachWindowByItsLength()
    {
        repository.save(row(0L, FIVE_MINUTES_MS, "c1", "api", 1.0));
        repository.save(row(FIVE_MINUTES_MS, FIVE_MINUTES_MS, "c1", "api", 1.0));
        repository.save(row(HOUR_MS, HOUR_MS, "c1", "api", 10.0));

        double average = repository.averageCpuPerInstant(0L, 2 * HOUR_MS, List.of("c1")).orElseThrow();

        assertEquals(36_600_000.0 / 4_200_000.0, average, 1e-5);
    }

    @Test
    void averageCpuPerInstant_sumsTheContainersOfAWindowBeforeWeighing()
    {
        repository.save(row(0L, HOUR_MS, "c1", "api", 2.0));
        repository.save(row(0L, HOUR_MS, "c1", "worker", 3.0));

        double average = repository.averageCpuPerInstant(0L, HOUR_MS, List.of("c1")).orElseThrow();

        assertEquals(5.0, average, 1e-5);
    }

    @Test
    void averageCpuPerInstant_withoutMatchingRows_isEmpty()
    {
        repository.save(row(0L, HOUR_MS, "c1", "api", 2.0));

        assertTrue(repository.averageCpuPerInstant(0L, HOUR_MS, List.of("other")).isEmpty());
    }

    /** Same reasoning as the average: a container's share is the CPU-time it accounts for. */
    @Test
    void containerShares_weighEachWindowByItsLength()
    {
        repository.save(row(0L, FIVE_MINUTES_MS, "c1", "short", 10.0));
        repository.save(row(HOUR_MS, HOUR_MS, "c1", "long", 10.0));

        Map<String, Double> shares = repository.containerShares(0L, 2 * HOUR_MS, List.of("c1"));

        assertEquals(2, shares.size());
        assertEquals(3_000_000.0 / 39_000_000.0, shares.get("short"), 1e-7);
        assertEquals(36_000_000.0 / 39_000_000.0, shares.get("long"), 1e-7);
    }

    private static KubernetesData source(KubernetesMetricEntity row)
    {
        return (KubernetesData) row.data;
    }
}
