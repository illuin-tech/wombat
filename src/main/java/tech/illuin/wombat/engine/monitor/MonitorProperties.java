package tech.illuin.wombat.engine.monitor;

import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import tech.illuin.wombat.core.source.Period;

public interface MonitorProperties
{
    Period heartbeat();

    @WithName("environments-file")
    @WithDefault("monitored/monitored-environments.yaml")
    String environmentsFile();
}
