package tech.illuin.wombat.persistence.backend.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.core.source.data.MetricData;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.persistence.backend.api.store.MetricSample;
import tech.illuin.wombat.persistence.backend.api.store.SkippedMetrics;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushMetricStoreTest
{
    private static final String TYPE = "tech.illuin.wombat-module.kubernetes-api";
    private static final long INSTANT_MS = 1_700_000_100_000L;
    private static final long WINDOW_MS = 300_000L;

    private static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final List<String> workers = new ArrayList<>();
    private final List<List<MetricSample<MetricData>>> requests = new ArrayList<>();
    private final PushMetricStore<KubernetesData> store = new PushMetricStore<>(this::accept, "east", MAPPER);

    @Test
    void save_sendsTheSamplesOfAWindowInOneRequest_asTheyAre_asItsWorker()
    {
        AssetIdentity first = AssetIdentity.of("first", "env", "First", TYPE);
        AssetIdentity second = AssetIdentity.of("second", "env", "Second", TYPE);
        List<MetricSample<KubernetesData>> samples = List.of(sample(first, "a"), sample(second, "b"), sample(first, "c"));

        this.store.save(samples);

        assertEquals(List.of("east"), this.workers);
        assertEquals(1, this.requests.size());
        assertEquals(samples, this.requests.getFirst());
    }

    /* What the aggregator answers when it skips some of them: decoded as plain maps, like Feign does. */
    @Test
    void save_readsTheMetricsTheAggregatorSkipped()
    {
        AssetIdentity asset = AssetIdentity.of("first", "env", "First", TYPE);
        Response<Integer> response = Response.success("Saved 0 metric(s), skipped 1", 0)
            .set(SkippedMetrics.STATUS_KEY, MAPPER.convertValue(List.of(new SkippedMetrics("first", TYPE, "No active module for asset type " + TYPE, 1)), List.class));
        PushMetricStore<KubernetesData> store = new PushMetricStore<>((worker, samples) -> response, "east", MAPPER);

        assertDoesNotThrow(() -> store.save(List.of(sample(asset, "a"))));
    }

    @Test
    void save_sendsNothingWithoutSamples()
    {
        this.store.save(List.of());

        assertTrue(this.requests.isEmpty());
    }

    private Response<Integer> accept(String worker, List<MetricSample<MetricData>> samples)
    {
        this.workers.add(worker);
        this.requests.add(samples);
        return Response.success(samples.size());
    }

    private static MetricSample<KubernetesData> sample(AssetIdentity asset, String pod)
    {
        return MetricSample.of(asset, INSTANT_MS, WINDOW_MS, data(pod));
    }

    private static KubernetesData data(String pod)
    {
        return new KubernetesData("api", "cluster", "namespace", pod, 1.0, 1.0);
    }
}
