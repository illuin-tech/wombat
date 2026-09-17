package tech.illuin.wombat.monitor;

import io.quarkus.runtime.annotations.RegisterForReflection;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.Environment;

import java.util.List;
import java.util.Map;

@RegisterForReflection
public record MonitoredEnvironments(Map<String, Environment> environments)
{
    public List<Asset> allAssets()
    {
        return this.environments.values().stream()
            .flatMap(environment -> environment.assets().stream())
            .toList();
    }
}
