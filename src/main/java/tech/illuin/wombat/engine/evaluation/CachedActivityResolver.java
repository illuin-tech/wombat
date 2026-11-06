package tech.illuin.wombat.engine.evaluation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import tech.illuin.wombat.core.activity.WombatActivityException;
import tech.illuin.wombat.core.activity.WombatActivityResolver;
import tech.illuin.wombat.core.activity.commons.AssetActivity;
import tech.illuin.wombat.core.activity.commons.AssetFilter;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.engine.evaluation.CachedAssetEvaluatorProperties.ResolverCacheProperties;

import java.util.Optional;
import java.util.Set;

import static tech.illuin.wombat.commons.ExceptionHelper.uncheck;

public class CachedActivityResolver implements WombatActivityResolver
{
    private final WombatActivityResolver delegate;
    private final Cache<ActivityCacheKey, Optional<AssetActivity>> cache;

    public CachedActivityResolver(WombatActivityResolver delegate, ResolverCacheProperties properties)
    {
        this.delegate = delegate;
        this.cache = Caffeine.newBuilder()
            .maximumSize(properties.maximumSize())
            .expireAfterWrite(properties.expireAfterWrite())
            .build();
    }

    @Override
    public boolean accept(Asset asset)
    {
        return this.delegate.accept(asset);
    }

    @Override @SuppressWarnings("RedundantThrows")
    public Optional<AssetActivity> resolve(Asset asset, TimeRange range, AssetFilter filter) throws WombatActivityException
    {
        Set<String> serviceIds = filter == null ? Set.of() : filter.filterServiceIds(asset);
        return this.cache.get(
            new ActivityCacheKey(asset.identity(), range, serviceIds),
            uncheck(_ -> this.delegate.resolve(asset, range, filter))
        );
    }

    public record ActivityCacheKey(AssetIdentity identity, TimeRange range, Set<String> serviceIds) {}
}
