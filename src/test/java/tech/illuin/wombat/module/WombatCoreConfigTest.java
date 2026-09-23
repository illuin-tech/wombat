package tech.illuin.wombat.module;

import io.quarkus.arc.All;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.WombatCore;
import tech.illuin.wombat.core.asset.ServiceFamily;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.core.source.persistence.WombatMetricPersister;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusModule;
import tech.illuin.wombat.module.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Augmentation already fails the build on an unsatisfied dependency, so this guards the step after: that the beans
 * assemble into something usable.
 * <p>
 * {@link AssetEvaluator} and {@link AssetMonitor} are resolved through {@link Instance} rather than injected
 * directly. Both are built by walking the {@code WombatContextProvider}, which reads the assets through Panache, so
 * creating either outside a transaction or request context throws {@code ContextNotActiveException}. The controllers
 * are safe because a JAX-RS request context is always active there — but any eager injection point is not.
 */
@QuarkusTest
class WombatCoreConfigTest
{
    @Inject WombatCore core;
    @Inject WombatMetricPersister persister;
    @Inject SecretResolver secretResolver;
    @Inject Instance<AssetEvaluator> calculator;
    @Inject Instance<AssetMonitor> monitor;
    @Inject List<WombatModule> modules;
    @Inject @All List<WombatModuleConfig.DefaultHandlers> defaultHandlers;

    @Test
    void theContextFreeEntryPointsAreWired()
    {
        assertNotNull(this.core);
        assertNotNull(this.persister);
        assertNotNull(this.secretResolver);
    }

    @Test
    void bothServiceFamiliesGetTheirDefaultHandlers()
    {
        assertEquals(
            Set.of(ServiceFamily.KUBERNETES_CONTAINER, ServiceFamily.LLM),
            this.defaultHandlers.stream().map(WombatModuleConfig.DefaultHandlers::family).collect(Collectors.toSet())
        );

        this.defaultHandlers.forEach(handlers -> {
            assertNotNull(handlers.activityResolver(), handlers.family().name());
            assertNotNull(handlers.impactResolver(), handlers.family().name());
        });
    }

    @Test
    void everyModuleIsWiredWhenNoToggleIsSet()
    {
        assertEquals(5, this.modules.size());
        assertTrue(this.modules.stream().anyMatch(KubernetesAPIModule.class::isInstance));
        assertTrue(this.modules.stream().anyMatch(KubernetesSimulatedModule.class::isInstance));
        assertTrue(this.modules.stream().anyMatch(LLMPrometheusModule.class::isInstance));
        assertTrue(this.modules.stream().anyMatch(LLMSimulatedModule.class::isInstance));
        assertTrue(this.modules.stream().anyMatch(LLMStaticModule.class::isInstance));
    }

    @Test
    @Transactional
    void theCalculatorResolvesAgainstThePersistedContext()
    {
        assertNotNull(this.calculator.get());
    }

    @Test
    @Transactional
    void theMonitorResolvesAgainstThePersistedContext()
    {
        // Deliberately not triggered: sampling dials the real clusters in the monitored-environments file, which
        // would make this a live integration test. That the monitor gets a source per sourceable asset — rather
        // than being a bare instance that samples nothing — is covered by WombatCoreTest in core.
        assertNotNull(this.monitor.get());
    }
}
