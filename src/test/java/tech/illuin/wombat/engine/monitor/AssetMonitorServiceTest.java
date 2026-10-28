package tech.illuin.wombat.engine.monitor;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.context.ResolvedContext;
import tech.illuin.wombat.core.context.WombatContextProvider;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.core.source.Period;
import tech.illuin.wombat.core.source.WombatSource;
import tech.illuin.wombat.core.source.data.MetricData;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusAsset;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusProfile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.illuin.wombat.core.activity.commons.Country.FRANCE;

class AssetMonitorServiceTest
{
    private static final long DEFAULT_HEARTBEAT_SECONDS = 1;

    @Test
    void assetPeriodLesserOrEqualToHeartbeat_runsOnEveryHeartbeat() throws InterruptedException
    {
        RecordingSource source = new RecordingSource();
        try (AssetMonitor monitor = monitorFor(
            source,
            prometheus("period-zero", Period.ZERO),
            prometheus("period-1s", Period.ofSeconds(1))
        )) {
            beat(monitor, 3, DEFAULT_HEARTBEAT_SECONDS);
        }

        assertEquals(3, source.calls.getOrDefault("period-zero", 0));
        assertEquals(3, source.calls.getOrDefault("period-1s", 0));
    }

    @Test
    void assetPeriodMultipleOfHeartbeat_runsWhenPeriodHasElapsed() throws InterruptedException
    {
        RecordingSource source = new RecordingSource();
        try (AssetMonitor monitor = monitorFor(source, prometheus("period-3s", Period.ofSeconds(3))))
        {
            // Heartbeat = 1s, Component = 3s.
            // Beat 1: fires (initial activation)
            // Beats 2, 3: skipped
            // Beat 4: fires (3s elapsed since beat 1)
            // Beats 5, 6: skipped
            // Beat 7: fires (3s elapsed since beat 4)
            beat(monitor, 7, DEFAULT_HEARTBEAT_SECONDS);
        }

        assertEquals(3, source.calls.getOrDefault("period-3s", 0));
    }

    @Test
    void activationPeriodIsIndependentPerAsset() throws InterruptedException
    {
        RecordingSource source = new RecordingSource();
        try (AssetMonitor monitor = monitorFor(
            source,
            prometheus("every-2s", Period.ofSeconds(2)),
            prometheus("every-3s", Period.ofSeconds(3))
        )) {
            beat(monitor, 6, DEFAULT_HEARTBEAT_SECONDS);
        }

        // every-2s fires on beats 1, 3, 5
        assertEquals(3, source.calls.getOrDefault("every-2s", 0));
        // every-3s fires on beats 1, 4
        assertEquals(2, source.calls.getOrDefault("every-3s", 0));
    }

    @Test
    void assetPeriodNotMultipleOfHeartbeat_runsOnFirstHeartbeatExceedingPeriod() throws InterruptedException
    {
        RecordingSource source = new RecordingSource();
        try (AssetMonitor monitor = monitorFor(source, prometheus("period-3s", Period.ofSeconds(3))))
        {
            // Heartbeat = 2s, Component = 3s.
            // Beat 1 (+2s): fires (initial activation)
            // Beat 2 (+4s): skipped (2s elapsed)
            // Beat 3 (+6s): fires (4s elapsed >= 3s)
            // Beat 4 (+8s): skipped (2s elapsed)
            // Beat 5 (+10s): fires (4s elapsed >= 3s)
            beat(monitor, 5, 2); // 2s heartbeat
        }

        assertEquals(3, source.calls.getOrDefault("period-3s", 0));
    }

    @Test
    void heartbeatGreaterThanAssetPeriod_runsOnEveryHeartbeatWithoutBursting() throws InterruptedException
    {
        RecordingSource source = new RecordingSource();
        try (AssetMonitor monitor = monitorFor(source, prometheus("period-1s", Period.ofSeconds(1))))
        {
            // Heartbeat is 5s, asset period is 1s.
            beat(monitor, 3, 5); // 5s heartbeat
        }

        assertEquals(3, source.calls.getOrDefault("period-1s", 0));
    }

    @Test
    void lastActivatedTimestampIsTrackedAcrossConsecutiveActivations() throws InterruptedException
    {
        RecordingSource source = new RecordingSource();
        try (AssetMonitor monitor = monitorFor(source, prometheus("period-2s", Period.ofSeconds(2))))
        {
            beat(monitor, 3, DEFAULT_HEARTBEAT_SECONDS); // fires on beats 1 and 3
        }

        List<RecordingSource.ActivationRecord> records = source.history.get("period-2s");
        assertNotNull(records);
        assertEquals(2, records.size());

        Instant firstBeatTime = records.get(0).heartbeat();
        Instant secondActivationLastActivated = records.get(1).lastActivated();

        assertEquals(firstBeatTime, secondActivationLastActivated);
        assertTrue(records.get(1).heartbeat().isAfter(records.get(0).heartbeat()));
    }

    @Test
    void assetsOfAnUnregisteredTypeAreSkipped() throws InterruptedException
    {
        RecordingSource source = new RecordingSource();
        try (AssetMonitor monitor = new AssetMonitor(
            contextOf(prometheus("orphan", Period.ZERO)),
            (asset, type, metrics) -> {}
        )) {
            beat(monitor, 3, DEFAULT_HEARTBEAT_SECONDS);
        }

        assertEquals(0, source.calls.size());
    }

    private static AssetMonitor monitorFor(RecordingSource source, LLMPrometheusAsset... assets)
    {
        AssetMonitor monitor = new AssetMonitor(contextOf(assets), (asset, type, metrics) -> {});
        monitor.register(assets[0].type(), source);
        return monitor;
    }

    private static WombatContextProvider contextOf(Asset... assets)
    {
        return () -> new ResolvedContext(List.of(new Environment("env", List.of(assets))));
    }

    /**
     * Pauses the thread for the duration of the heartbeat (plus a small safety buffer),
     * then triggers the monitor. This relies naturally on Instant.now() progressing.
     */
    private static void beat(AssetMonitor monitor, int times, long stepSeconds) throws InterruptedException
    {
        for (int i = 0; i < times; ++i)
        {
            // A 50ms buffer ensures Thread.sleep doesn't wake up fractionally early
            // and fail a period elapsed check (e.g. 998ms < 1000ms).
            Thread.sleep(stepSeconds * 1000 + 50);
            monitor.trigger();
        }
    }

    private static LLMPrometheusAsset prometheus(String id, Period period)
    {
        return new LLMPrometheusAsset(
            AssetIdentity.of(id, "env", id),
            "http://prometheus", null, null, null, period,
            new LLMPrometheusProfile(LLMProvider.mistralai, "m", FRANCE, new LLMPrometheusProfile.DynamicProfile("q"))
        );
    }

    private static final class RecordingSource implements WombatSource
    {
        record ActivationRecord(Instant heartbeat, Instant lastActivated) { }

        private final Map<String, Integer> calls = new ConcurrentHashMap<>();
        private final Map<String, List<ActivationRecord>> history = new ConcurrentHashMap<>();

        @Override
        public List<MetricData> source(Instant heartbeat, Instant lastActivated, Asset asset)
        {
            String assetId = asset.identity().id();
            this.calls.merge(assetId, 1, Integer::sum);
            this.history.computeIfAbsent(assetId, k -> new CopyOnWriteArrayList<>())
                .add(new ActivationRecord(heartbeat, lastActivated));
            return emptyList();
        }
    }
}
