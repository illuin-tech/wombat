package tech.illuin.wombat.engine.monitor;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import tech.illuin.wombat.core.source.Period;

@ConfigMapping(prefix = "monitor")
public interface MonitorProperties
{
    Period heartbeat();

    @WithName("environments-file")
    @WithDefault("monitored/monitored-environments.yaml")
    String environmentsFile();
}
