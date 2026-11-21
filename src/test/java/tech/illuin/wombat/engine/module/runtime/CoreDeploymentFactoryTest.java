package tech.illuin.wombat.engine.module.runtime;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.activity.WombatActivityException;
import tech.illuin.wombat.core.activity.WombatActivityResolver;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.profile.AssetProfile;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.context.ResolvedContext;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.module.ProcessorFactory;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.engine.evaluation.CachedAssetEvaluator;
import tech.illuin.wombat.engine.evaluation.CachedAssetEvaluatorProperties;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreDeploymentFactoryTest
{
    private static final String K8S = KubernetesSimulatedModule.TYPE.name();
    private static final String LLM = LLMSimulatedModule.TYPE.name();

    @Test
    void onlyTheRequestedModulesAreDeployed()
    {
        CoreDeployment deployment = factory(true).create(1, "id", Set.of(LLM));

        assertEquals(Set.of(LLMSimulatedModule.TYPE), deployment.types());
        assertTrue(deployment.missing().isEmpty());
        deployment.close();
    }

    @Test
    void aRequestedTypeWithNoDetectedModuleIsReportedMissing()
    {
        CoreDeployment deployment = factory(true).create(1, "id", Set.of(K8S, "acme.unknown"));

        assertEquals(Set.of(KubernetesSimulatedModule.TYPE), deployment.types());
        assertEquals(Set.of("acme.unknown"), deployment.missing());
        deployment.close();
    }

    @Test
    void theServedEvaluatorIsCachedWhenTheCacheIsEnabled()
    {
        CoreDeployment deployment = factory(true).create(1, "id", Set.of(K8S, LLM));

        assertInstanceOf(CachedAssetEvaluator.class, deployment.evaluator());
        deployment.close();
    }

    @Test
    void theServedEvaluatorIsNotCachedWhenTheCacheIsDisabled()
    {
        CoreDeployment deployment = factory(false).create(1, "id", Set.of(K8S, LLM));

        assertFalse(deployment.evaluator() instanceof CachedAssetEvaluator);
        deployment.close();
    }

    @Test
    void newEvaluatorBuildsAnUncachedEvaluatorOfItsOwnOnEachCall()
    {
        CoreDeployment deployment = factory(true).create(1, "id", Set.of(K8S, LLM));

        try (AssetEvaluator first = deployment.newEvaluator(); AssetEvaluator second = deployment.newEvaluator()) {
            assertFalse(first instanceof CachedAssetEvaluator);
            assertNotSame(deployment.evaluator(), first);
            assertNotSame(first, second);
        }
        deployment.close();
    }

    @Test
    void aNewEvaluatorBuildsItsOwnComponents_andClosingItLeavesTheModulesOpen() throws WombatActivityException
    {
        CountingModule module = new CountingModule();
        CoreDeployment deployment = factory(true, List.of(module)).create(1, "id", Set.of(CountingModule.TYPE.name()));
        CountedAsset asset = new CountedAsset(AssetIdentity.of("a1", "e1", CountingModule.TYPE), null);
        TimeRange range = TimeRange.between(Instant.EPOCH, Instant.EPOCH.plus(Duration.ofHours(1)));

        deployment.evaluator().computeActivity(asset, range, null);
        try (AssetEvaluator evaluator = deployment.newEvaluator()) {
            evaluator.computeActivity(asset, range, null);
        }

        assertEquals(2, module.builds.get());
        assertFalse(module.closed);
        deployment.close();
    }

    @Test
    void eachDeploymentGetsItsOwnMonitorAndEvaluatorButSharesTheModules()
    {
        CoreDeploymentFactory factory = factory(true);
        CoreDeployment first = factory.create(1, "id", Set.of(K8S));
        CoreDeployment second = factory.create(1, "id", Set.of(K8S));

        assertNotSame(first.monitor(), second.monitor());
        assertNotSame(first.evaluator(), second.evaluator());
        assertSame(first.modules().getFirst(), second.modules().getFirst());
        first.close();
        second.close();
    }

    private static CoreDeploymentFactory factory(boolean cache)
    {
        return factory(cache, List.of(new KubernetesSimulatedModule(), new LLMSimulatedModule()));
    }

    private static CoreDeploymentFactory factory(boolean cache, List<WombatModule> modules)
    {
        return new CoreDeploymentFactory(
            () -> new ResolvedContext(List.of()),
            SecretResolver.standard(),
            (identity, type, metrics) -> {},
            modules,
            List.of(),
            new CacheProperties(cache, 1000L, Duration.ofMinutes(10), List.of())
        );
    }

    private record CacheProperties(
        boolean enabled,
        long maximumSize,
        Duration expireAfterWrite,
        List<ResolverCacheProperties> activityResolverCaches
    ) implements CachedAssetEvaluatorProperties {}

    /* Counts the activity-resolvers it builds, which tells whether two evaluators share their components */
    private static final class CountingModule implements WombatModule
    {
        static final AssetType TYPE = AssetType.of("acme", "test", "counting", ActivityRegime.MODELED, ServiceFamily.LLM);

        final AtomicInteger builds = new AtomicInteger();
        boolean closed;

        @Override
        public AssetType type()
        {
            return TYPE;
        }

        @Override
        public Class<? extends Asset> assetClass()
        {
            return CountedAsset.class;
        }

        @Override
        public Optional<ProcessorFactory<WombatActivityResolver>> activityResolver()
        {
            return Optional.of((services, asset) -> {
                this.builds.incrementAndGet();
                return (a, range, filter) -> Optional.empty();
            });
        }

        @Override
        public void close()
        {
            this.closed = true;
        }
    }

    private record CountedAsset(AssetIdentity identity, AssetProfile profile) implements Asset
    {
        @Override
        public AssetType type()
        {
            return CountingModule.TYPE;
        }
    }
}
