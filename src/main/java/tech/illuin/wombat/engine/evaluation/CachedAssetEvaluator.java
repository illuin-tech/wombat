package tech.illuin.wombat.engine.evaluation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import tech.illuin.wombat.core.activity.WombatActivityException;
import tech.illuin.wombat.core.activity.WombatActivityResolver;
import tech.illuin.wombat.core.activity.commons.ActivityData;
import tech.illuin.wombat.core.activity.commons.AssetActivity;
import tech.illuin.wombat.core.activity.commons.AssetFilter;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.type.ModuleType;
import tech.illuin.wombat.core.evaluation.AssetEvaluation;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.evaluation.EvaluationReport;
import tech.illuin.wombat.core.evaluation.WombatEvaluationException;
import tech.illuin.wombat.core.evaluation.WombatEvaluationResolver;
import tech.illuin.wombat.core.evaluation.WombatProfileResolver;
import tech.illuin.wombat.core.module.ProcessorFactory;
import tech.illuin.wombat.engine.evaluation.CachedAssetEvaluatorProperties.ResolverCacheProperties;

import java.util.Optional;

import static tech.illuin.wombat.commons.ExceptionHelper.uncheck;

public class CachedAssetEvaluator extends AssetEvaluator
{
    private final AssetEvaluator delegate;
    private final CachedAssetEvaluatorProperties properties;
    private final Cache<EvaluateCacheKey, EvaluationReport> evaluateCache;
    private final Cache<ActivityCacheKey, Optional<AssetActivity>> activityCache;
    private final Cache<ImpactCacheKey, Optional<AssetEvaluation>> impactCache;
    private final Cache<CostCacheKey, Optional<AssetEvaluation>> costCache;

    public CachedAssetEvaluator(AssetEvaluator delegate, CachedAssetEvaluatorProperties properties)
    {
        super(null, null);
        this.delegate = delegate;
        this.properties = properties;
        this.evaluateCache = createCache(properties);
        this.activityCache = createCache(properties);
        this.impactCache = createCache(properties);
        this.costCache = createCache(properties);
    }

    private static <K, V> Cache<K, V> createCache(CachedAssetEvaluatorProperties properties)
    {
        return Caffeine.newBuilder()
            .maximumSize(properties.maximumSize())
            .expireAfterWrite(properties.expireAfterWrite())
            .build();
    }

    @Override @SuppressWarnings("RedundantThrows")
    public EvaluationReport report(TimeRange timeRange, AssetFilter filter) throws WombatEvaluationException
    {
        return this.evaluateCache.get(
            new EvaluateCacheKey(timeRange, filter),
            uncheck(k -> this.delegate.report(timeRange, filter))
        );
    }

    @Override @SuppressWarnings("RedundantThrows")
    public Optional<AssetActivity> computeActivity(Asset asset, TimeRange timeRange, AssetFilter filter) throws WombatActivityException
    {
        return this.activityCache.get(
            new ActivityCacheKey(asset.identity(), timeRange, filter),
            uncheck(k -> this.delegate.computeActivity(asset, timeRange, filter))
        );
    }

    @Override @SuppressWarnings("RedundantThrows")
    public Optional<AssetEvaluation> computeImpact(Asset asset, ActivityData data) throws WombatEvaluationException
    {
        return this.impactCache.get(
            new ImpactCacheKey(asset.identity(), data),
            uncheck(k -> this.delegate.computeImpact(asset, data))
        );
    }

    @Override @SuppressWarnings("RedundantThrows")
    public Optional<AssetEvaluation> computeCost(Asset asset, ActivityData data) throws WombatEvaluationException
    {
        return this.costCache.get(
            new CostCacheKey(asset.identity(), data),
            uncheck(k -> this.delegate.computeCost(asset, data))
        );
    }

    @Override
    public AssetEvaluator registerActivityResolver(ModuleType type, ProcessorFactory<WombatActivityResolver> factory)
    {
        this.hasModuleCache(type).ifPresentOrElse(
            cache -> this.delegate.registerActivityResolver(type, (services, asset) -> new CachedActivityResolver(factory.create(services, asset), cache)),
            () -> this.delegate.registerActivityResolver(type, factory)
        );
        return this;
    }

    @Override
    public AssetEvaluator registerCostResolver(ModuleType type, ProcessorFactory<WombatEvaluationResolver> factory)
    {
        this.delegate.registerCostResolver(type, factory);
        return this;
    }

    @Override
    public AssetEvaluator registerImpactResolver(ModuleType type, ProcessorFactory<WombatEvaluationResolver> factory)
    {
        this.delegate.registerImpactResolver(type, factory);
        return this;
    }

    @Override
    public AssetEvaluator registerProfileResolver(ModuleType type, ProcessorFactory<WombatProfileResolver> factory)
    {
        this.delegate.registerProfileResolver(type, factory);
        return this;
    }

    @Override
    public void refresh(AssetIdentity identity)
    {
        this.delegate.refresh(identity);
        this.activityCache.asMap().keySet().removeIf(key -> key.identity().equals(identity));
        this.impactCache.asMap().keySet().removeIf(key -> key.identity().equals(identity));
        this.costCache.asMap().keySet().removeIf(key -> key.identity().equals(identity));
        // Evaluations are cached for whole ranges across all assets, the refreshed asset's share cannot be singled out.
        this.evaluateCache.invalidateAll();
    }

    private Optional<ResolverCacheProperties> hasModuleCache(ModuleType type)
    {
        return this.properties.activityResolverCaches().stream()
            .filter(cache -> cache.module().equals(type.name()))
            .findFirst();
    }

    @Override
    public void close()
    {
        super.close();
        this.evaluateCache.invalidateAll();
        this.activityCache.invalidateAll();
        this.impactCache.invalidateAll();
        this.costCache.invalidateAll();
        this.delegate.close();
    }

    public record EvaluateCacheKey(TimeRange timeRange, AssetFilter filter) {}

    public record ActivityCacheKey(AssetIdentity identity, TimeRange timeRange, AssetFilter filter) {}

    public record ImpactCacheKey(AssetIdentity identity, ActivityData data) {}

    public record CostCacheKey(AssetIdentity identity, ActivityData data) {}
}
