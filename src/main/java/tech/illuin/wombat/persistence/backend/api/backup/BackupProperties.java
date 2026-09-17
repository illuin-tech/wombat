package tech.illuin.wombat.persistence.backend.api.backup;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import tech.illuin.wombat.persistence.backend.s3.S3Properties;

import java.util.Optional;

@ConfigMapping(prefix = "backup")
public interface BackupProperties
{
    @WithDefault("false")
    boolean enabled();

    @WithDefault("0 0 2 * * ?")
    String cron();

    @WithDefault("true")
    boolean restoreOnStartup();

    Optional<S3Properties> s3();

    CleanupProperties cleanup();

    interface CleanupProperties
    {
        @WithDefault("true")
        boolean enabled();

        @WithDefault("0 0 3 * * ?")
        String cron();

        @WithDefault("10")
        int retainLast();
    }
}
