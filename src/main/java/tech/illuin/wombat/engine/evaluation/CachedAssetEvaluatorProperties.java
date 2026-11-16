package tech.illuin.wombat.engine.evaluation;

import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;
import java.util.List;

public interface CachedAssetEvaluatorProperties
{
    @WithDefault("true")
    boolean enabled();

    @WithName("maximum-size")
    @WithDefault("5000")
    long maximumSize();

    @WithName("expire-after-write")
    @WithDefault("10m")
    Duration expireAfterWrite();

    @WithName("activity-resolver-caches")
    List<ResolverCacheProperties> activityResolverCaches();

    interface ResolverCacheProperties
    {
        @WithName("module")
        String module();

        @WithName("maximum-size")
        @WithDefault("5000")
        long maximumSize();

        @WithName("expire-after-write")
        @WithDefault("10m")
        Duration expireAfterWrite();
    }
}
