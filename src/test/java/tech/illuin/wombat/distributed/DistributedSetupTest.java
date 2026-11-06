package tech.illuin.wombat.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import feign.FeignException;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.activity.commons.Country;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.asset.profile.Lifetime;
import tech.illuin.wombat.core.asset.profile.ServerProvider;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.core.source.data.LLMData;
import tech.illuin.wombat.core.source.data.MetricData;
import tech.illuin.wombat.core.source.persistence.WombatMetricPersister;
import tech.illuin.wombat.core.source.Period;
import tech.illuin.wombat.core.source.persistence.micrometer.MicrometerMetricPersister;
import tech.illuin.wombat.engine.context.persistence.AssetEntity;
import tech.illuin.wombat.engine.context.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.persistence.EnvironmentRepository;
import tech.illuin.wombat.engine.monitor.MonitoredEnvironments;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIAsset;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIServerProfile;
import tech.illuin.wombat.persistence.backend.push.AggregatorProperties;
import tech.illuin.wombat.persistence.backend.push.PushBackendConfig;
import tech.illuin.wombat.persistence.backend.push.WombatEnvironmentClient;
import tech.illuin.wombat.persistence.micrometer.MetricsConfig;
import tech.illuin.wombat.persistence.micrometer.WombatStepMeterRegistry;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.Role;
import tech.illuin.wombat.security.apikey.ApiKeyService;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.illuin.wombat.core.source.persistence.micrometer.MicrometerTags.*;

/**
 * An aggregator (this application, with authentication on) and two workers sampling separate environments.
 * <p>
 * The workers are the worker-mode pipeline built in-process: the persister sources write to, the step registry
 * aggregating their samples, the push stores sending each closed window to the aggregator with an API key, and the
 * client declaring their environments. Each
 * worker has its own clock, so the test closes their windows instead of waiting 5 minutes. On the aggregator, the test
 * checks what it staged; writing a closed window to the database is the same step as for local sources.
 */
@QuarkusTest
@TestProfile(DistributedSetupTest.AggregatorProfile.class)
class DistributedSetupTest
{
    private static final String KUBERNETES_TYPE = "tech.illuin.wombat-module.kubernetes-api";
    private static final String LLM_TYPE = "tech.illuin.wombat-module.llm-static";
    private static final Country FRANCE = Country.ofCode("FRA").orElseThrow();

    @TestHTTPResource("/")
    URL url;

    @Inject
    ObjectMapper mapper;

    /* What a worker declares its environments with: it knows the asset types from its creation. */
    @Inject
    JsonMapper assetMapper;

    @Inject
    AssetRepository assets;

    @Inject
    EnvironmentRepository environments;

    @Inject
    ApiKeyService keys;

    @Inject
    List<WombatModule> modules;

    /* The cumulative registry: what the aggregator staged, before its own window closes and writes it. */
    @Inject
    PrometheusMeterRegistry aggregatorRegistry;

    private Worker east;
    private Worker west;

    @BeforeEach
    void setUp()
    {
        String apiKey = this.issueWorkerKey();
        this.east = new Worker("east", this.aggregator(apiKey));
        this.west = new Worker("west", this.aggregator(apiKey));
    }

    @AfterEach
    void tearDown()
    {
        this.east.close();
        this.west.close();
    }

    @Test
    void workers_pushTheirWindowsToTheAggregator_whichStagesThemPerAsset() throws Exception
    {
        AssetIdentity eastCluster = AssetIdentity.of("east-cluster", "east", "East Cluster");
        AssetIdentity eastLlm = AssetIdentity.of("east-llm", "east", "East LLM");
        AssetIdentity westCluster = AssetIdentity.of("west-cluster", "west", "West Cluster");
        AssetIdentity westLlm = AssetIdentity.of("west-llm", "west", "West LLM");

        /* Several source heartbeats within one window on each worker. */
        this.east.sample(eastCluster, this.type(KUBERNETES_TYPE), pod("api", 100.0, 1024.0));
        this.east.sample(eastCluster, this.type(KUBERNETES_TYPE), pod("api", 300.0, 1024.0));
        this.east.sample(eastLlm, this.type(LLM_TYPE), tokens(50L));
        this.west.sample(westCluster, this.type(KUBERNETES_TYPE), pod("api", 500.0, 2048.0));
        this.west.sample(westLlm, this.type(LLM_TYPE), tokens(70L));
        this.west.sample(westLlm, this.type(LLM_TYPE), tokens(30L));

        this.east.closeWindow();
        this.west.closeWindow();

        /* One sample per asset: what each worker aggregated over its window, the mean CPU/RAM and the summed tokens. */
        this.assertStaged(METRIC_K8S_CPU, eastCluster, KUBERNETES_TYPE, 200.0);
        this.assertStaged(METRIC_K8S_RAM, eastCluster, KUBERNETES_TYPE, 1024.0);
        this.assertStaged(METRIC_K8S_CPU, westCluster, KUBERNETES_TYPE, 500.0);
        this.assertStaged(METRIC_K8S_RAM, westCluster, KUBERNETES_TYPE, 2048.0);
        this.assertStaged(METRIC_LLM_OUTPUT_TOKENS, eastLlm, LLM_TYPE, 50.0);
        this.assertStaged(METRIC_LLM_OUTPUT_TOKENS, westLlm, LLM_TYPE, 100.0);
    }

    @Test
    void workers_declareTheirEnvironments_whichTheAggregatorEvaluatesAsTheirs()
    {
        this.east.declare(environment("east", cluster("east", "east-declared")));
        this.west.declare(environment("west", cluster("west", "west-declared")));

        assertEquals("east", this.asset("east-declared").owner);
        assertEquals("east", this.asset("east-declared").environmentId);
        assertEquals("west", this.asset("west-declared").owner);
        assertNull(this.environments.findById("east").disabledAt);
        assertNull(this.environments.findById("west").disabledAt);
    }

    @Test
    void worker_removesAnAssetByNoLongerDeclaringIt()
    {
        this.east.declare(environment("east", cluster("east", "east-kept"), cluster("east", "east-removed")));
        this.east.declare(environment("east", cluster("east", "east-kept")));

        assertNull(this.asset("east-kept").deletedAt);
        assertNotNull(this.asset("east-removed").deletedAt);
    }

    /* test-cluster comes from the aggregator's own environments file. */
    @Test
    void worker_cannotDeclareAnAssetOwnedByTheAggregator()
    {
        FeignException conflict = assertThrows(FeignException.class, () -> this.east.declare(environment("test", cluster("test", "test-cluster"))));

        assertEquals(409, conflict.status());
        assertNull(this.asset("test-cluster").owner);
    }

    @Test
    void worker_cannotDeclareAnAssetOwnedByAnotherWorker()
    {
        this.east.declare(environment("east", cluster("east", "east-claimed")));

        FeignException conflict = assertThrows(FeignException.class, () -> this.west.declare(environment("east", cluster("east", "east-claimed"))));

        assertEquals(409, conflict.status());
        assertEquals("east", this.asset("east-claimed").owner);
    }

    private AssetEntity asset(String id)
    {
        return this.assets.findById(id);
    }

    private static MonitoredEnvironments environment(String id, Asset... assets)
    {
        return new MonitoredEnvironments(Map.of(id, new Environment(id, List.of(assets))));
    }

    private static KubernetesAPIAsset cluster(String environment, String id)
    {
        return new KubernetesAPIAsset(
            AssetIdentity.of(id, environment, id),
            "kubeconfig/" + environment + ".config.yaml", "default", Optional.empty(), Optional.empty(), Period.ZERO,
            new KubernetesAPIServerProfile(ServerProvider.aws, "c5.large", FRANCE, Lifetime.ofYears(5))
        );
    }

    private void assertStaged(String metric, AssetIdentity asset, String type, double value)
    {
        DistributionSummary summary = this.aggregatorRegistry.find(metric)
            .tag(TAG_ASSET, asset.id())
            .tag(TAG_ENVIRONMENT, asset.environmentId())
            .tag(TAG_ASSET_TYPE, type)
            .summary();
        assertNotNull(summary, metric + " of " + asset.id());
        assertEquals(1, summary.count(), metric + " of " + asset.id());
        assertEquals(value, summary.totalAmount(), 1e-9, metric + " of " + asset.id());
    }

    private AssetType type(String name)
    {
        return this.modules.stream().map(WombatModule::type).filter(type -> type.name().equals(name)).findFirst().orElseThrow();
    }

    private String issueWorkerKey()
    {
        Caller admin = new Caller("https://idp", UUID.randomUUID().toString(), "admin", "admin@example.com", EnumSet.of(Role.ADMIN), Role.ADMIN.permissions());
        return this.keys.create(admin, "worker", List.of(Permission.Names.API_METRIC_WRITE, Permission.Names.API_ENVIRONMENT_WRITE), Instant.now().plus(Duration.ofDays(1))).token();
    }

    private AggregatorProperties aggregator(String apiKey)
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
                return apiKey;
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

    private static KubernetesData pod(String container, double cpu, double ram)
    {
        return new KubernetesData(container, "cluster", "namespace", container + "-pod", cpu, ram);
    }

    private static LLMData tokens(long outputTokens)
    {
        return new LLMData("chat", LLMProvider.mistralai, "mistral-large-latest", FRANCE, outputTokens, 1L);
    }

    /** What a worker-mode instance wires around its sources. */
    private class Worker implements AutoCloseable
    {
        private final String id;
        private final MockClock clock = new MockClock();
        private final WombatStepMeterRegistry registry;
        private final WombatMetricPersister persister;
        private final WombatEnvironmentClient declarations;

        Worker(String id, AggregatorProperties aggregator)
        {
            this.id = id;
            this.declarations = PushBackendConfig.createClient(WombatEnvironmentClient.class, aggregator, DistributedSetupTest.this.assetMapper);
            this.registry = new WombatStepMeterRegistry(manualConfig(), this.clock, PushBackendConfig.stores(aggregator, DistributedSetupTest.this.mapper));
            this.persister = new MicrometerMetricPersister(this.registry);
        }

        void sample(AssetIdentity asset, AssetType type, MetricData data) throws Exception
        {
            this.persister.persist(asset, type, List.of(data));
        }

        void declare(MonitoredEnvironments environments)
        {
            this.declarations.declare(this.id, environments);
        }

        void closeWindow()
        {
            this.clock.add(MetricsConfig.SAMPLING);
            this.registry.flush();
        }

        @Override
        public void close()
        {
            this.registry.close();
        }

        /** Disabled so the registry never publishes on its own schedule, nor on close. */
        private static StepRegistryConfig manualConfig()
        {
            return new StepRegistryConfig()
            {
                @Override
                public String prefix()
                {
                    return "wombat";
                }

                @Override
                public Duration step()
                {
                    return MetricsConfig.SAMPLING;
                }

                @Override
                public boolean enabled()
                {
                    return false;
                }

                @Override
                public String get(@NonNull String key)
                {
                    return null;
                }
            };
        }
    }

    public static class AggregatorProfile extends AuthenticationEnabledTest.AuthenticationEnabledProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            Map<String, String> overrides = new HashMap<>(super.getConfigOverrides());
            overrides.put("wombat.mode", "aggregator");
            return overrides;
        }
    }
}
