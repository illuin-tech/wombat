package tech.illuin.wombat.engine.module.runtime;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.context.ResolvedContext;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.engine.evaluation.CachedAssetEvaluator;
import tech.illuin.wombat.engine.evaluation.CachedAssetEvaluatorProperties;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        CoreDeployment deployment = factory(true).create(Set.of(LLM));

        assertEquals(Set.of(LLMSimulatedModule.TYPE), deployment.types());
        assertTrue(deployment.missing().isEmpty());
        deployment.close();
    }

    @Test
    void aRequestedTypeWithNoDetectedModuleIsReportedMissing()
    {
        CoreDeployment deployment = factory(true).create(Set.of(K8S, "acme.unknown"));

        assertEquals(Set.of(KubernetesSimulatedModule.TYPE), deployment.types());
        assertEquals(Set.of("acme.unknown"), deployment.missing());
        deployment.close();
    }

    @Test
    void theCachedEvaluatorWrapsTheUncachedOneWhenTheCacheIsEnabled()
    {
        CoreDeployment deployment = factory(true).create(Set.of(K8S, LLM));

        assertInstanceOf(CachedAssetEvaluator.class, deployment.evaluator());
        assertNotSame(deployment.evaluator(), deployment.uncachedEvaluator());
        deployment.close();
    }

    @Test
    void bothEvaluatorsAreTheSameWhenTheCacheIsDisabled()
    {
        CoreDeployment deployment = factory(false).create(Set.of(K8S, LLM));

        assertSame(deployment.evaluator(), deployment.uncachedEvaluator());
        deployment.close();
    }

    @Test
    void eachDeploymentGetsItsOwnMonitorAndEvaluatorButSharesTheModules()
    {
        CoreDeploymentFactory factory = factory(true);
        CoreDeployment first = factory.create(Set.of(K8S));
        CoreDeployment second = factory.create(Set.of(K8S));

        assertNotSame(first.monitor(), second.monitor());
        assertNotSame(first.evaluator(), second.evaluator());
        assertSame(first.modules().getFirst(), second.modules().getFirst());
        first.close();
        second.close();
    }

    private static CoreDeploymentFactory factory(boolean cache)
    {
        List<WombatModule> modules = List.of(new KubernetesSimulatedModule(), new LLMSimulatedModule());
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
}
