package tech.illuin.wombat.feature;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.commons.NodeMode;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureTest
{
    @Test
    void standaloneServesTheUiTheReadApiAndTheAdministration()
    {
        assertEquals(Set.of(Feature.UI, Feature.API_READ, Feature.ADMIN), enabled(NodeMode.standalone));
    }

    @Test
    void aggregatorServesEveryFeature()
    {
        assertEquals(Set.of(Feature.values()), enabled(NodeMode.aggregator));
    }

    @Test
    void workerServesNoFeature()
    {
        assertEquals(Set.of(), enabled(NodeMode.worker));
    }

    @Test
    void configKeysMatchTheConstants()
    {
        assertEquals(Feature.Keys.UI, Feature.UI.configKey());
        assertEquals(Feature.Keys.API_READ, Feature.API_READ.configKey());
        assertEquals(Feature.Keys.API_WRITE, Feature.API_WRITE.configKey());
        assertEquals(Feature.Keys.ADMIN, Feature.ADMIN.configKey());
    }

    private static Set<Feature> enabled(NodeMode mode)
    {
        return Arrays.stream(Feature.values())
            .filter(feature -> feature.enabledIn(mode))
            .collect(Collectors.toSet());
    }
}
