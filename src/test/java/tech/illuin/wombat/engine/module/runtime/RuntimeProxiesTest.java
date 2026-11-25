package tech.illuin.wombat.engine.module.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.activity.commons.AssetFilter;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.evaluation.AssetEvaluation;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.evaluation.WombatEvaluationException;
import tech.illuin.wombat.core.source.AssetMonitor;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeProxiesTest
{
    private CoreDeploymentFactory factory;
    private CoreRuntime runtime;

    @BeforeEach
    void setUp()
    {
        this.factory = mock(CoreDeploymentFactory.class);
        this.runtime = new CoreRuntime(this.factory, Runnable::run);
    }

    /* A public method left to the parent would silently run on its empty pools: a core bump adding one must fail here */
    @Test
    void theEvaluatorOverridesEveryPublicMethodOfTheCoreEvaluator()
    {
        assertOverridesEveryPublicMethod(AssetEvaluator.class, RuntimeAssetEvaluator.class);
    }

    @Test
    void theMonitorOverridesEveryPublicMethodOfTheCoreMonitor()
    {
        assertOverridesEveryPublicMethod(AssetMonitor.class, RuntimeAssetMonitor.class);
    }

    @Test
    void theEvaluatorFollowsTheCurrentDeployment() throws WombatEvaluationException
    {
        CoreDeployment first = deployment();
        CoreDeployment second = deployment();
        when(this.factory.create(anyLong(), anyString(), any())).thenReturn(first, second);
        TimeRange range = mock(TimeRange.class);
        AssetFilter filter = mock(AssetFilter.class);
        List<AssetEvaluation> evaluations = List.of(mock(AssetEvaluation.class));
        when(second.evaluator().evaluate(range, filter)).thenReturn(evaluations);

        RuntimeAssetEvaluator evaluator = new RuntimeAssetEvaluator(this.runtime);
        this.runtime.deploy(1, "id-1", List.of());
        evaluator.evaluate(range, filter);
        verify(first.evaluator()).evaluate(range, filter);

        this.runtime.deploy(2, "id-2", List.of());
        assertSame(evaluations, evaluator.evaluate(range, filter));
    }

    @Test
    void theMonitorFollowsTheCurrentDeployment()
    {
        CoreDeployment first = deployment();
        CoreDeployment second = deployment();
        when(this.factory.create(anyLong(), anyString(), any())).thenReturn(first, second);
        AssetIdentity identity = new AssetIdentity("asset", "env", "acme.type");

        RuntimeAssetMonitor monitor = new RuntimeAssetMonitor(this.runtime);
        this.runtime.deploy(1, "id-1", List.of());
        monitor.trigger();
        verify(first.monitor()).trigger();

        this.runtime.deploy(2, "id-2", List.of());
        monitor.trigger();
        monitor.refresh(identity);
        verify(second.monitor()).trigger();
        verify(second.monitor()).refresh(identity);
    }

    @Test
    void registeringOnTheProxiesIsRefused()
    {
        assertThrows(UnsupportedOperationException.class, () -> new RuntimeAssetEvaluator(this.runtime).registerActivityResolver(null, null));
        assertThrows(UnsupportedOperationException.class, () -> new RuntimeAssetMonitor(this.runtime).register(null, null));
    }

    private static void assertOverridesEveryPublicMethod(Class<?> core, Class<?> proxy)
    {
        List<String> missing = Arrays.stream(core.getMethods())
            .filter(method -> !Modifier.isStatic(method.getModifiers()))
            .filter(method -> method.getDeclaringClass() != Object.class)
            .filter(method -> !isDeclaredBy(proxy, method))
            .map(Method::toGenericString)
            .toList();

        assertEquals(List.of(), missing, proxy.getSimpleName() + " must override every public method of " + core.getSimpleName());
    }

    private static boolean isDeclaredBy(Class<?> type, Method method)
    {
        try {
            type.getDeclaredMethod(method.getName(), method.getParameterTypes());
            return true;
        }
        catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static CoreDeployment deployment()
    {
        AssetEvaluator evaluator = mock(AssetEvaluator.class);
        return new CoreDeployment(0, "id", List.of(), Set.of(), mock(AssetMonitor.class), evaluator, () -> evaluator, Instant.now());
    }
}
