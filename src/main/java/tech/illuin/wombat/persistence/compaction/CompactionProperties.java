package tech.illuin.wombat.persistence.compaction;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;

@ConfigMapping(prefix = "compaction")
public interface CompactionProperties
{
    @WithDefault("0 1 * * * ?")
    String cron();

    @WithDefault("1h")
    Duration window();

    @WithDefault("1m")
    Duration delay();
}
