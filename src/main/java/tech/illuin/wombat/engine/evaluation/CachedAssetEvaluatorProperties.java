package tech.illuin.wombat.engine.evaluation;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;

@ConfigMapping(prefix = "evaluation.cache")
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
}
