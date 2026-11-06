package tech.illuin.wombat.persistence.backend.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.endpoint.AggregatorProfile;
import tech.illuin.wombat.engine.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.persistence.model.KubernetesMetricEntity;
import tech.illuin.wombat.persistence.backend.api.store.MetricSample;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pushes from a worker's store to this instance acting as the aggregator, and checks the samples are saved with their
 * identity, type included, at the instant of the worker's window.
 */
@QuarkusTest
@TestProfile(AggregatorProfile.class)
class PushBackendTest
{
    @TestHTTPResource("/")
    URL url;

    @Inject
    ObjectMapper mapper;

    @Inject
    KubernetesMetricRepository kubernetesMetrics;

    @Test
    void pushedSamples_areSavedOnTheAggregator()
    {
        WombatPushClient client = PushBackendConfig.createClient(WombatPushClient.class, this.properties(), this.mapper);
        PushMetricStore<KubernetesData> store = new PushMetricStore<>(client, "east", this.mapper);
        AssetIdentity asset = AssetIdentity.of("pushed-cluster", "remote", "Pushed Cluster", "tech.illuin.wombat-module.kubernetes-api");
        AssetIdentity saved = AssetIdentity.of("east.pushed-cluster", "remote", "Pushed Cluster", "tech.illuin.wombat-module.kubernetes-api");
        QuarkusTransaction.requiringNew().run(() -> this.kubernetesMetrics.delete("assetId", saved.id()));

        store.save(List.of(MetricSample.of(asset, 1_700_000_100_000L, 300_000L, new KubernetesData("api", "cluster", "ns", "pod", 250.0, 4096.0))));

        List<KubernetesMetricEntity> rows = this.kubernetesMetrics.list("assetId", saved.id());
        assertEquals(1, rows.size());
        assertEquals(saved, rows.getFirst().identity());
        assertEquals(1_700_000_100_000L, rows.getFirst().instantMs);
        assertEquals(250.0, rows.getFirst().cpuNanocores, 1e-9);
    }

    private AggregatorProperties properties()
    {
        String base = this.url.toString();
        return new AggregatorProperties()
        {
            @Override
            public String url()
            {
                return base;
            }

            @Override
            public String apiKey()
            {
                return "unused-without-authentication";
            }

            @Override
            public Optional<String> proxyUrl()
            {
                return Optional.empty();
            }

            @Override
            public Optional<Duration> timeout()
            {
                return Optional.of(Duration.ofSeconds(5));
            }
        };
    }
}
