package tech.illuin.wombat.monitor;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.context.ResolvedContext;
import tech.illuin.wombat.core.context.WombatContextProvider;
import tech.illuin.wombat.core.source.WombatSource;
import tech.illuin.wombat.core.source.WombatSourceException;
import tech.illuin.wombat.core.source.data.MetricData;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusAsset;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusProfile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetMonitorServiceTest
{

    @Test
    void heartbeatSkipOfZeroOrOne_runsOnEveryHeartbeat()
    {
        RecordingSource source = new RecordingSource();
        try (AssetMonitor monitor = monitorFor(source, prometheus("skip-0", 0), prometheus("skip-1", 1)))
        {
            beat(monitor, 3);
        }

        assertEquals(3, source.calls.getOrDefault("skip-0", 0));
        assertEquals(3, source.calls.getOrDefault("skip-1", 0));
    }

    @Test
    void heartbeatSkipOfN_runsOnEveryNthHeartbeat()
    {
        RecordingSource source = new RecordingSource();
        try (AssetMonitor monitor = monitorFor(source, prometheus("skip-3", 3)))
        {
            beat(monitor, 7); // beats 1..7 -> fires on 3 and 6
        }

        assertEquals(2, source.calls.getOrDefault("skip-3", 0));
    }

    @Test
    void heartbeatSkipIsIndependentPerAsset()
    {
        // Regression: a shared per-asset-evaluation counter made each asset's cadence depend on the
        // others, so an asset could fire on every heartbeat or never. The cadence must be per-asset.
        RecordingSource source = new RecordingSource();
        try (AssetMonitor monitor = monitorFor(source, prometheus("every-2", 2), prometheus("every-3", 3)))
        {
            beat(monitor, 6);
        }

        assertEquals(3, source.calls.getOrDefault("every-2", 0)); // beats 2, 4, 6
        assertEquals(2, source.calls.getOrDefault("every-3", 0)); // beats 3, 6
    }

    @Test
    void assetsOfAnUnregisteredTypeAreSkipped()
    {
        RecordingSource source = new RecordingSource();
        // No source registered at all, so nothing may be sampled however many heartbeats elapse.
        try (AssetMonitor monitor = new AssetMonitor(contextOf(prometheus("orphan", 0)), metrics -> { }))
        {
            beat(monitor, 3);
        }

        assertEquals(0, source.calls.size());
    }

    private static AssetMonitor monitorFor(RecordingSource source, LLMPrometheusAsset... assets)
    {
        AssetMonitor monitor = new AssetMonitor(contextOf(assets), metrics -> { });
        // Sources are registered per asset-type, so any asset of that type wires the whole type.
        monitor.register(assets[0], source);
        return monitor;
    }

    private static WombatContextProvider contextOf(Asset... assets)
    {
        return () -> new ResolvedContext(List.of(new Environment("env", List.of(assets))));
    }

    private static void beat(AssetMonitor monitor, int times)
    {
        for (int i = 0; i < times; i++)
            monitor.trigger();
    }

    private static LLMPrometheusAsset prometheus(String id, int heartbeatSkip)
    {
        return new LLMPrometheusAsset(id, "env", id, "http://prometheus", null, null, null, heartbeatSkip,
            new LLMPrometheusProfile(LLMProvider.mistralai, "m", "FRA", new LLMPrometheusProfile.DynamicProfile("q")));
    }

    private static final class RecordingSource implements WombatSource
    {
        /** Sampling runs on the monitor's executor threads, so the tally has to be safe to write from any of them. */
        private final Map<String, Integer> calls = new ConcurrentHashMap<>();

        @Override
        public List<MetricData> source(Instant heartbeat, Asset asset) throws WombatSourceException {
            this.calls.merge(asset.id(), 1, Integer::sum);
            return emptyList();
        }
    }
}
