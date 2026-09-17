package tech.illuin.wombat.monitor;


import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "monitor")
public interface MonitorProperties
{
    String heartbeat();

    @WithName("environments-file")
    @WithDefault("monitored/monitored-environments.yaml")
    String environmentsFile();
}
