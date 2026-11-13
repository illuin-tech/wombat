package tech.illuin.wombat.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import feign.FeignException;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusModule;
import tech.illuin.wombat.module.llm_anthropic.LLMAnthropicModule;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
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
import tech.illuin.wombat.engine.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.engine.impact.llm.LLMModelMetricRepository;
import tech.illuin.wombat.engine.monitor.MonitoredEnvironments;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIAsset;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIServerProfile;
import tech.illuin.wombat.module.llm_anthropic.LLMAnthropicAsset;
import tech.illuin.wombat.module.llm_anthropic.LLMAnthropicProfile;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusAsset;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusProfile;
import tech.illuin.wombat.persistence.backend.push.AggregatorProperties;
import tech.illuin.wombat.persistence.backend.push.PushBackendConfig;
import tech.illuin.wombat.persistence.backend.push.WombatEnvironmentClient;
import tech.illuin.wombat.persistence.micrometer.MetricsConfig;
import tech.illuin.wombat.persistence.model.KubernetesMetricEntity;
import tech.illuin.wombat.persistence.model.LLMMetricEntity;
import tech.illuin.wombat.persistence.micrometer.WombatStepMeterRegistry;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.Role;
import tech.illuin.wombat.security.apikey.ApiKeyService;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An aggregator (this application, with authentication on) and two workers sampling separate environments.
 * <p>
 * The workers are the worker-mode pipeline built in-process: the persister sources write to, the step registry
 * aggregating their samples, the push stores sending each closed window to the aggregator with an API key, and the
 * client declaring their environments. Each
 * worker has its own clock, so the test closes their windows instead of waiting 5 minutes. On the aggregator, the test
 * checks the rows it saved, at the instant of each worker's window.
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

    @Inject
    KubernetesMetricRepository kubernetesMetrics;

    @Inject
    LLMModelMetricRepository llmMetrics;

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

    /* Both workers sample assets with the same ids: the aggregator keeps them apart. */
    @Test
    void workers_pushTheirWindowsToTheAggregator_whichSavesThemAtTheWorkersInstant() throws Exception
    {
        AssetIdentity eastCluster = AssetIdentity.of("cluster", "east", "Cluster", KUBERNETES_TYPE);
        AssetIdentity eastLlm = AssetIdentity.of("llm", "east", "LLM", LLM_TYPE);
        AssetIdentity westCluster = AssetIdentity.of("cluster", "west", "Cluster", KUBERNETES_TYPE);
        AssetIdentity westLlm = AssetIdentity.of("llm", "west", "LLM", LLM_TYPE);
        List<String> assetIds = List.of("east.cluster", "east.llm", "west.cluster", "west.llm");
        QuarkusTransaction.requiringNew().run(() -> {
            this.kubernetesMetrics.delete("assetId in ?1", assetIds);
            this.llmMetrics.delete("assetId in ?1", assetIds);
        });

        /* Several source heartbeats within one window on each worker. */
        this.east.sample(eastCluster, this.type(KUBERNETES_TYPE), pod("api", 100.0, 1024.0));
        this.east.sample(eastCluster, this.type(KUBERNETES_TYPE), pod("api", 300.0, 1024.0));
        this.east.sample(eastLlm, this.type(LLM_TYPE), tokens(50L));
        this.west.sample(westCluster, this.type(KUBERNETES_TYPE), pod("api", 500.0, 2048.0));
        this.west.sample(westLlm, this.type(LLM_TYPE), tokens(70L));
        this.west.sample(westLlm, this.type(LLM_TYPE), tokens(30L));

        long eastWindow = this.east.closeWindow();
        long westWindow = this.west.closeWindow();

        /*
         * One row per asset, saved as soon as it is pushed: what each worker aggregated over its window, the mean CPU/RAM
         * and the summed tokens, at the start of that window on the worker's clock.
         */
        this.assertSaved(owned("east", eastCluster), KUBERNETES_TYPE, eastWindow, 200.0, 1024.0);
        this.assertSaved(owned("west", westCluster), KUBERNETES_TYPE, westWindow, 500.0, 2048.0);
        this.assertSaved(owned("east", eastLlm), LLM_TYPE, eastWindow, 50L);
        this.assertSaved(owned("west", westLlm), LLM_TYPE, westWindow, 100L);
    }

    @Test
    void workers_declareTheirEnvironments_whichTheAggregatorEvaluatesAsTheirs()
    {
        this.east.declare(environment("east", cluster("east", "declared")));
        this.west.declare(environment("west", cluster("west", "declared")));

        assertEquals("east", this.asset("east.declared").owner);
        assertEquals("east", this.asset("east.declared").environmentId);
        assertEquals("east.declared", this.asset("east.declared").properties.identity().id());
        assertEquals("west", this.asset("west.declared").owner);
        assertNull(this.asset("declared"));
        assertNull(this.environments.findById("east").disabledAt);
        assertNull(this.environments.findById("west").disabledAt);
    }

    @Test
    void worker_removesAnAssetByNoLongerDeclaringIt()
    {
        this.east.declare(environment("east", cluster("east", "kept"), cluster("east", "removed")));
        this.east.declare(environment("east", cluster("east", "kept")));

        assertNull(this.asset("east.kept").deletedAt);
        assertNotNull(this.asset("east.removed").deletedAt);
    }

    /* test-cluster comes from the aggregator's own environments file. */
    @Test
    void worker_declaresAnAssetIdOfTheAggregator_asItsOwnAsset()
    {
        this.east.declare(environment("test", cluster("test", "test-cluster")));

        assertEquals("east", this.asset("east.test-cluster").owner);
        assertNull(this.asset("test-cluster").owner);
    }

    @Test
    void workers_declaringTheSameAssetId_declareTwoAssets()
    {
        this.east.declare(environment("shared", cluster("shared", "shared-cluster")));
        this.west.declare(environment("shared", cluster("shared", "shared-cluster")));

        assertEquals("east", this.asset("east.shared-cluster").owner);
        assertEquals("west", this.asset("west.shared-cluster").owner);
        assertNull(this.asset("east.shared-cluster").deletedAt);
    }

    /* Anthropic has no source, so nothing for a worker to sample: it is declared in the aggregator's environments file. */
    @Test
    void worker_cannotDeclareAnUnsampledAsset()
    {
        MonitoredEnvironments declaration = environment("east", anthropic("east", "east-anthropic", "EAST_ANTHROPIC_ADMIN_KEY"));

        FeignException rejected = assertThrows(FeignException.class, () -> this.east.declare(declaration));

        assertEquals(400, rejected.status());
        assertNull(this.asset("east.east-anthropic"));
    }

    @Test
    void worker_cannotDeclareWithAnUppercaseId()
    {
        try (Worker upper = new Worker("East", this.aggregator(this.issueWorkerKey())))
        {
            FeignException rejected = assertThrows(FeignException.class, () -> upper.declare(environment("east", cluster("east", "east-upper"))));

            assertEquals(400, rejected.status());
            assertNull(this.asset("East.east-upper"));
        }
    }

    /* The Prometheus password is used by the source, which runs on the worker: the aggregator never needs it. */
    @Test
    void worker_declaresASampledAssetRequiringASecret_whichTheAggregatorDoesNotHold()
    {
        this.east.declare(environment("east", prometheus("east", "east-llm", "EAST_PROMETHEUS_PASSWORD")));

        assertEquals("east", this.asset("east.east-llm").owner);
    }

    private static AssetIdentity owned(String worker, AssetIdentity asset)
    {
        return AssetIdentity.of(worker + "." + asset.id(), asset.environmentId(), asset.name(), asset.assetType());
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
            AssetIdentity.of(id, environment, id, KubernetesAPIModule.TYPE),
            "kubeconfig/" + environment + ".config.yaml", "default", Optional.empty(), Optional.empty(), Period.ZERO,
            new KubernetesAPIServerProfile(ServerProvider.aws, "c5.large", FRANCE, Lifetime.ofYears(5))
        );
    }

    private static LLMAnthropicAsset anthropic(String environment, String id, String adminKey)
    {
        return new LLMAnthropicAsset(
            AssetIdentity.of(id, environment, id, LLMAnthropicModule.TYPE),
            null, null, adminKey,
            new LLMAnthropicProfile(FRANCE, 500, List.of(), List.of(), List.of())
        );
    }

    private static LLMPrometheusAsset prometheus(String environment, String id, String passwordKey)
    {
        return new LLMPrometheusAsset(
            AssetIdentity.of(id, environment, id, LLMPrometheusModule.TYPE),
            "http://prometheus", null, "user", passwordKey, Period.ZERO,
            new LLMPrometheusProfile(LLMProvider.mistralai, "mistral-large-latest", FRANCE, new LLMPrometheusProfile.DynamicProfile("q"))
        );
    }

    private void assertSaved(AssetIdentity asset, String type, long instantMs, double cpu, double ram)
    {
        List<KubernetesMetricEntity> rows = this.kubernetesMetrics.list("assetId", asset.id());
        assertEquals(1, rows.size(), asset.id());
        KubernetesMetricEntity row = rows.getFirst();
        assertEquals(asset, row.identity());
        assertEquals(type, row.assetType);
        assertEquals(instantMs, row.instantMs, asset.id());
        assertEquals(MetricsConfig.SAMPLING.toMillis(), row.windowMs, asset.id());
        assertEquals(cpu, row.cpuNanocores, 1e-9, asset.id());
        assertEquals(ram, row.ramBytes, 1e-9, asset.id());
    }

    private void assertSaved(AssetIdentity asset, String type, long instantMs, long outputTokens)
    {
        List<LLMMetricEntity> rows = this.llmMetrics.list("assetId", asset.id());
        assertEquals(1, rows.size(), asset.id());
        LLMMetricEntity row = rows.getFirst();
        assertEquals(asset, row.identity());
        assertEquals(type, row.assetType);
        assertEquals(instantMs, row.instantMs, asset.id());
        assertEquals(outputTokens, row.outputTokens, asset.id());
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
            this.registry = new WombatStepMeterRegistry(manualConfig(), this.clock, PushBackendConfig.stores(aggregator, id, DistributedSetupTest.this.mapper));
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

        /** @return the start of the window closed, on this worker's clock */
        long closeWindow()
        {
            long windowStartMs = this.clock.wallTime() / MetricsConfig.SAMPLING.toMillis() * MetricsConfig.SAMPLING.toMillis();
            this.clock.add(MetricsConfig.SAMPLING);
            this.registry.flush();
            return windowStartMs;
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
            overrides.put("wombat.secret.write-mode", "database");
            overrides.put("wombat.secret.database.active-kek-id", "test");
            overrides.put("wombat.secret.database.kek.test", Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
            ));
            return overrides;
        }
    }
}
