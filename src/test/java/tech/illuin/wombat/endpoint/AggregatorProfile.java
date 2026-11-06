package tech.illuin.wombat.endpoint;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class AggregatorProfile implements QuarkusTestProfile
{
    @Override
    public Map<String, String> getConfigOverrides()
    {
        return Map.of("wombat.mode", "aggregator");
    }
}
