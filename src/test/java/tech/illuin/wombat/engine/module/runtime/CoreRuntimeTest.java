package tech.illuin.wombat.engine.module.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreRuntimeTest
{
    private CoreDeploymentFactory factory;
    private List<Runnable> pendingCloses;
    private CoreRuntime runtime;

    @BeforeEach
    void setUp()
    {
        this.factory = mock(CoreDeploymentFactory.class);
        this.pendingCloses = new ArrayList<>();
        /* Holds the delayed closes, so that tests decide when the grace delay is over */
        this.runtime = new CoreRuntime(this.factory, this.pendingCloses::add);
    }

    @Test
    void currentBeforeTheFirstDeploymentThrows()
    {
        assertThrows(IllegalStateException.class, this.runtime::current);
    }

    @Test
    void aDeploySwapsInTheNewDeploymentAtOnce()
    {
        CoreDeployment second = deployment(2);
        when(this.factory.create(anyLong(), anyString(), any())).thenReturn(deployment(1), second);

        this.runtime.deploy(1, "one", Set.of());
        this.runtime.deploy(2, "two", Set.of());

        assertSame(second, this.runtime.current());
    }

    @Test
    void aVersionNotNewerThanTheRunningOneIsIgnored()
    {
        CoreDeployment second = deployment(2);
        when(this.factory.create(anyLong(), anyString(), any())).thenReturn(second);
        this.runtime.deploy(2, "two", Set.of());

        assertSame(second, this.runtime.deploy(1, "one", Set.of()));
        assertSame(second, this.runtime.deploy(2, "two", Set.of()));
        verify(this.factory, times(1)).create(anyLong(), anyString(), any());
    }

    @Test
    void thePreviousDeploymentIsClosedAfterTheGraceDelay()
    {
        CoreDeployment first = deployment(1);
        CoreDeployment second = deployment(2);
        when(this.factory.create(anyLong(), anyString(), any())).thenReturn(first, second);
        this.runtime.deploy(1, "one", Set.of());
        this.runtime.deploy(2, "two", Set.of());

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
        CoreDeployment first = deployment(1);
        when(this.factory.create(anyLong(), anyString(), any())).thenReturn(first).thenThrow(new IllegalStateException("broken module"));

        this.runtime.deploy(1, "one", Set.of());
        assertThrows(IllegalStateException.class, () -> this.runtime.deploy(2, "two", Set.of()));

        assertSame(first, this.runtime.current());
        assertTrue(this.pendingCloses.isEmpty());
    }

    @Test
    void closingClosesTheCurrentDeploymentButNotItsModules()
    {
        WombatModule module = mock(WombatModule.class);
        when(module.type()).thenReturn(KubernetesSimulatedModule.TYPE);
        CoreDeployment deployment = deployment(1, module);
        when(this.factory.create(anyLong(), anyString(), any())).thenReturn(deployment);
        this.runtime.deploy(1, "one", Set.of());

        this.runtime.close();

        verify(deployment.monitor()).close();
        verify(deployment.evaluator()).close();
        verify(module, never()).close();
    }

    @Test
    void aShortIdIsTheFirstSevenCharacters()
    {
        assertEquals("a3f9c21", CoreRuntime.shortId("a3f9c21e8b0d"));
        assertEquals("abc", CoreRuntime.shortId("abc"));
    }

    /* Monitor and evaluator are mocks, so that closing can be verified without a context */
    static CoreDeployment deployment(long version, WombatModule... modules)
    {
        AssetEvaluator evaluator = mock(AssetEvaluator.class);
        return new CoreDeployment(version, "id-" + version, List.of(modules), Set.of(), mock(AssetMonitor.class), evaluator, evaluator, Instant.now());
    }
}
