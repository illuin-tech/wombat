package tech.illuin.wombat.engine.module.runtime;

import tech.illuin.wombat.core.activity.WombatActivityException;
import tech.illuin.wombat.core.activity.WombatActivityResolver;
import tech.illuin.wombat.core.activity.commons.ActivityData;
import tech.illuin.wombat.core.activity.commons.AssetActivity;
import tech.illuin.wombat.core.activity.commons.AssetFilter;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.evaluation.AssetEvaluation;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.evaluation.WombatEvaluationException;
import tech.illuin.wombat.core.evaluation.WombatEvaluationResolver;
import tech.illuin.wombat.core.module.ProcessorFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The app's {@link AssetEvaluator}: each call runs on the evaluator of the current {@link CoreDeployment}. A subclass
 * because the core evaluator is a class; every public method is overridden, so the parent's own state is never used.
 */
public class RuntimeAssetEvaluator extends AssetEvaluator
{
    private final CoreRuntime runtime;

    public RuntimeAssetEvaluator(CoreRuntime runtime)
    {
        super(null, null);
        this.runtime = runtime;
    }

    @Override
    public <E extends AssetEvaluation> List<E> evaluate(TimeRange range, AssetFilter filter, Class<E> evalType) throws WombatEvaluationException
    {
        return this.runtime.current().evaluator().evaluate(range, filter, evalType);
    }

    @Override
    public List<AssetEvaluation> evaluate(TimeRange range, AssetFilter filter) throws WombatEvaluationException
    {
        return this.runtime.current().evaluator().evaluate(range, filter);
    }

    @Override
    public void refresh(AssetIdentity identity)
    {
        this.runtime.current().evaluator().refresh(identity);
    }

    @Override
    public Optional<AssetActivity> computeActivity(Asset asset, TimeRange range, AssetFilter filter) throws WombatActivityException
    {
        return this.runtime.current().evaluator().computeActivity(asset, range, filter);
    }

    @Override
    public Optional<AssetEvaluation> computeImpact(Asset asset, ActivityData activity) throws WombatEvaluationException
    {
        return this.runtime.current().evaluator().computeImpact(asset, activity);
    }

    @Override
    public Optional<AssetEvaluation> computeCost(Asset asset, ActivityData activity) throws WombatEvaluationException
    {
        return this.runtime.current().evaluator().computeCost(asset, activity);
    }

    @Override
    public CompletableFuture<Optional<AssetEvaluation>> computeImpactAsync(Asset asset, ActivityData activity)
    {
        return this.runtime.current().evaluator().computeImpactAsync(asset, activity);
    }

    @Override
    public CompletableFuture<Optional<AssetEvaluation>> computeCostAsync(Asset asset, ActivityData activity)
    {
        return this.runtime.current().evaluator().computeCostAsync(asset, activity);
    }

    /** Resolvers are registered by {@link CoreDeploymentFactory}, from the deployed modules. */
    @Override
    public AssetEvaluator registerActivityResolver(AssetType type, ProcessorFactory<WombatActivityResolver> factory)
    {
        throw new UnsupportedOperationException("Resolvers come from the deployed modules");
    }

    @Override
    public AssetEvaluator registerCostResolver(AssetType type, ProcessorFactory<WombatEvaluationResolver> factory)
    {
        throw new UnsupportedOperationException("Resolvers come from the deployed modules");
    }

    @Override
    public AssetEvaluator registerImpactResolver(AssetType type, ProcessorFactory<WombatEvaluationResolver> factory)
    {
        throw new UnsupportedOperationException("Resolvers come from the deployed modules");
    }

    /** Only closes the parent's unused executor: the deployments are closed by {@link CoreRuntime}. */
    @Override
    public void close()
    {
        super.close();
    }
}
