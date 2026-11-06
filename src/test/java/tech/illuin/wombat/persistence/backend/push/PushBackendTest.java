package tech.illuin.wombat.persistence.backend.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.endpoint.AggregatorProfile;
import tech.illuin.wombat.persistence.store.MetricSample;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static tech.illuin.wombat.core.source.persistence.micrometer.MicrometerTags.*;

/**
 * Pushes from a worker's store to this instance acting as the aggregator, and checks the samples are staged like
 * those of a local source.
 */
@QuarkusTest
@TestProfile(AggregatorProfile.class)
class PushBackendTest
{
    @TestHTTPResource("/")
    URL url;

    @Inject
    ObjectMapper mapper;

    /* The cumulative registry: the step one only reports a window once it has closed. */
    @Inject
    PrometheusMeterRegistry registry;

    @Test
    void pushedSamples_areStagedOnTheAggregator()
    {
        WombatPushClient client = PushBackendConfig.createClient(WombatPushClient.class, this.properties(), this.mapper);
        PushMetricStore<KubernetesData> store = new PushMetricStore<>(client);
        AssetIdentity asset = AssetIdentity.of("pushed-cluster", "remote", "Pushed Cluster");

        store.save(List.of(new MetricSample<>(asset, "tech.illuin.wombat-module.kubernetes-api", 0L, 300_000L, new KubernetesData("api", "cluster", "ns", "pod", 250.0, 4096.0))));

        DistributionSummary cpu = this.registry.find(METRIC_K8S_CPU).tag(TAG_ASSET, "pushed-cluster").tag(TAG_ENVIRONMENT, "remote").summary();
        assertNotNull(cpu);
        assertEquals(1, cpu.count());
        assertEquals(250.0, cpu.totalAmount(), 1e-9);
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
