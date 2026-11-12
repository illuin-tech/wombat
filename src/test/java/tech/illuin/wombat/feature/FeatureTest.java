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
        assertEquals(Set.of(Feature.UI, Feature.API_READ, Feature.ADMIN), enabled(NodeMode.STANDALONE));
    }

    @Test
    void aggregatorServesEveryFeature()
    {
        assertEquals(Set.of(Feature.values()), enabled(NodeMode.AGGREGATOR));
    }

    @Test
    void workerServesNoFeature()
    {
        assertEquals(Set.of(), enabled(NodeMode.WORKER));
    }

    private static Set<Feature> enabled(NodeMode mode)
    {
        return Arrays.stream(Feature.values())
            .filter(feature -> feature.enabledIn(mode))
            .collect(Collectors.toSet());
    }
}
