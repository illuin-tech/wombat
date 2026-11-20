package tech.illuin.wombat.engine.module;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.engine.module.runtime.CoreRuntime;
import tech.illuin.wombat.engine.module.runtime.RuntimeAssetEvaluator;

/**
 * Exposes the evaluator of the running core. The core itself is assembled by
 * {@link tech.illuin.wombat.engine.module.runtime.CoreDeploymentFactory}, and swapped on redeploy by {@link CoreRuntime}.
 */
@ApplicationScoped
public class WombatCoreConfig
{
    /**
     * Delegates to the current deployment on every call, so that its holders follow redeploys.
     */
    @Singleton
    public AssetEvaluator provideAssetEvaluator(CoreRuntime runtime)
    {
        return new RuntimeAssetEvaluator(runtime);
    }
}
