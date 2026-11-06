package tech.illuin.wombat.commons;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import tech.illuin.wombat.persistence.backend.push.WorkerProperties;

@ConfigMapping(prefix = "wombat")
public interface WombatProperties
{
    @WithDefault("standalone")
    NodeMode mode();

    WorkerProperties worker();
}
