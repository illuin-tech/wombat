package tech.illuin.wombat.engine.evaluation;

import io.quarkus.runtime.configuration.DurationConverter;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CachedAssetEvaluatorPropertiesTest
{
    @Test
    void missingActivityResolverCachesDefaultsToAnEmptyList()
    {
        CachedAssetEvaluatorProperties properties = load(Map.of());

        assertEquals(List.of(), properties.activityResolverCaches());
    }

    @Test
    void indexedActivityResolverCacheUsesItsModuleAndDefaultCacheSettings()
    {
        CachedAssetEvaluatorProperties properties = load(Map.of(
            "evaluation.cache.activity-resolver-caches[0].module", "tech.custom:llm",
            "evaluation.cache.activity-resolver-caches[1].module", "tech.custom:container",
            "evaluation.cache.activity-resolver-caches[1].maximum-size", "25",
            "evaluation.cache.activity-resolver-caches[1].expire-after-write", "2m"
        ));

        assertEquals(2, properties.activityResolverCaches().size());
        assertEquals("tech.custom:llm", properties.activityResolverCaches().getFirst().module());
        assertEquals(5000, properties.activityResolverCaches().getFirst().maximumSize());
        assertEquals(Duration.ofMinutes(10), properties.activityResolverCaches().getFirst().expireAfterWrite());
        assertEquals("tech.custom:container", properties.activityResolverCaches().getLast().module());
        assertEquals(25, properties.activityResolverCaches().getLast().maximumSize());
        assertEquals(Duration.ofMinutes(2), properties.activityResolverCaches().getLast().expireAfterWrite());
    }

    private static CachedAssetEvaluatorProperties load(Map<String, String> values)
    {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
            .withMapping(CachedAssetEvaluatorProperties.class)
            .withConverter(Duration.class, 100, new DurationConverter())
            .withSources(new PropertiesConfigSource(values, "test", 100))
            .build();
        return config.getConfigMapping(CachedAssetEvaluatorProperties.class);
    }
}