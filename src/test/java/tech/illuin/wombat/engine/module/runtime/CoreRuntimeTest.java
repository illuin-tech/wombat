package tech.illuin.wombat.engine.module.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.engine.module.registry.InMemoryModuleRegistry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreRuntimeTest
{
    private CoreDeploymentFactory factory;
    private InMemoryModuleRegistry registry;
    private List<Runnable> pendingCloses;
    private CoreRuntime runtime;

    @BeforeEach
    void setUp()
    {
        this.factory = mock(CoreDeploymentFactory.class);
        this.registry = new InMemoryModuleRegistry();
        this.pendingCloses = new ArrayList<>();
        /* Holds the delayed closes, so that tests decide when the grace delay is over */
        this.runtime = new CoreRuntime(this.factory, this.registry, this.pendingCloses::add);
    }

    @Test
    void currentBeforeTheFirstDeploymentThrows()
    {
        assertThrows(IllegalStateException.class, this.runtime::current);
    }

    @Test
    void startupDeploysTheEnabledModulesOfTheRegistry()
    {
        this.registry.registerModule(new KubernetesSimulatedModule(), true);
        this.registry.registerModule(new LLMSimulatedModule(), false);
        when(this.factory.create(any())).thenReturn(deployment());

        this.runtime.onStart(null);

        verify(this.factory).create(Set.of(KubernetesSimulatedModule.TYPE.name()));
    }

    @Test
    void aDeploySwapsInTheNewDeploymentAtOnce()
    {
        CoreDeployment second = deployment();
        when(this.factory.create(any())).thenReturn(deployment(), second);

        this.runtime.deploy(Set.of());
        this.runtime.deploy(Set.of());

        assertSame(second, this.runtime.current());
    }

    @Test
    void thePreviousDeploymentIsClosedAfterTheGraceDelay()
    {
        CoreDeployment first = deployment();
        CoreDeployment second = deployment();
        when(this.factory.create(any())).thenReturn(first, second);
        this.runtime.deploy(Set.of());
        this.runtime.deploy(Set.of());

        verify(first.monitor(), never()).close();
        verify(first.evaluator(), never()).close();

        this.pendingCloses.forEach(Runnable::run);
        verify(first.monitor()).close();
        verify(first.evaluator()).close();
        verify(second.monitor(), never()).close();
        verify(second.evaluator(), never()).close();
    }

    @Test
    void aFailedBuildKeepsTheRunningDeployment()
    {
        CoreDeployment first = deployment();
        when(this.factory.create(any())).thenReturn(first).thenThrow(new IllegalStateException("broken module"));

        this.runtime.deploy(Set.of());
        assertThrows(IllegalStateException.class, () -> this.runtime.deploy(Set.of()));

        assertSame(first, this.runtime.current());
        assertTrue(this.pendingCloses.isEmpty());
    }

    @Test
    void closingClosesTheCurrentDeploymentButNotItsModules()
    {
        WombatModule module = mock(WombatModule.class);
        when(module.type()).thenReturn(KubernetesSimulatedModule.TYPE);
        CoreDeployment deployment = deployment(module);
        when(this.factory.create(any())).thenReturn(deployment);
        this.runtime.deploy(Set.of());

        this.runtime.close();

        verify(deployment.monitor()).close();
        verify(deployment.evaluator()).close();
        verify(module, never()).close();
    }

    /* Monitor and evaluator are mocks, so that closing can be verified without a context */
    private static CoreDeployment deployment(WombatModule... modules)
    {
        AssetEvaluator evaluator = mock(AssetEvaluator.class);
        return new CoreDeployment(List.of(modules), Set.of(), mock(AssetMonitor.class), evaluator, evaluator, Instant.now());
    }
}
