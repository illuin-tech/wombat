package tech.illuin.wombat.persistence.backend.push;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.persistence.push.MetricPush;
import tech.illuin.wombat.persistence.store.MetricSample;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushMetricStoreTest
{
    private static final String TYPE = "tech.illuin.wombat-module.kubernetes-api";

    private final List<List<MetricPush>> requests = new ArrayList<>();
    private final PushMetricStore<KubernetesData> store = new PushMetricStore<>(this.requests::add);

    @Test
    void save_sendsTheSamplesOfAWindowInOneRequest_groupedByAsset()
    {
        AssetIdentity first = AssetIdentity.of("first", "env", "First");
        AssetIdentity second = AssetIdentity.of("second", "env", "Second");

        this.store.save(List.of(sample(first, "a"), sample(second, "b"), sample(first, "c")));

        assertEquals(1, this.requests.size());
        assertEquals(
            List.of(
                new MetricPush(first, TYPE, List.of(data("a"), data("c"))),
                new MetricPush(second, TYPE, List.of(data("b")))
            ),
            this.requests.getFirst()
        );
    }

    @Test
    void save_sendsNothingWithoutSamples()
    {
        this.store.save(List.of());

        assertTrue(this.requests.isEmpty());
    }

    private static MetricSample<KubernetesData> sample(AssetIdentity asset, String pod)
    {
        return new MetricSample<>(asset, TYPE, 0L, 300_000L, data(pod));
    }

    private static KubernetesData data(String pod)
    {
        return new KubernetesData("api", "cluster", "namespace", pod, 1.0, 1.0);
    }
}
