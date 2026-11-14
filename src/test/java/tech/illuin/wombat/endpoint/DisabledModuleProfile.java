package tech.illuin.wombat.endpoint;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/** An aggregator which disabled the kubernetes-api module. */
public class DisabledModuleProfile implements QuarkusTestProfile
{
    @Override
    public Map<String, String> getConfigOverrides()
    {
        return Map.of(
            "wombat.mode", "aggregator",
            "wombat.module.kubernetes-api.enabled", "false"
        );
    }
}
