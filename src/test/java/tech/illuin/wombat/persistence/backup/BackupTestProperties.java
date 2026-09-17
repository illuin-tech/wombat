package tech.illuin.wombat.persistence.backup;

import io.soabase.recordbuilder.core.RecordBuilder;
import tech.illuin.wombat.persistence.backend.api.backup.BackupProperties;
import tech.illuin.wombat.persistence.backend.s3.S3Properties;
import tech.illuin.wombat.persistence.backend.s3.S3TestPropertiesBuilder;

import java.util.Optional;

@RecordBuilder
public record BackupTestProperties(
    boolean enabled,
    String cron,
    boolean restoreOnStartup,
    Optional<S3Properties> s3,
    BackupProperties.CleanupProperties cleanup
) implements BackupProperties {
    public static final String DEFAULT_BUCKET = "wombat-backups";
    private static final String DEFAULT_CRON = "0 0 0 ? * MON#5 2099";

    public static BackupTestPropertiesBuilder ofDefault()
    {
        return BackupTestPropertiesBuilder.builder()
            .enabled(true)
            .cron(DEFAULT_CRON)
            .restoreOnStartup(false)
            .s3(Optional.of(S3TestPropertiesBuilder.builder()
                .bucket(DEFAULT_BUCKET)
                .region("us-east-1")
                .keyPrefix("")
                .build()))
            .cleanup(CleanupTestPropertiesBuilder.builder()
                .enabled(true)
                .cron(DEFAULT_CRON)
                .retainLast(0)
                .build());
    }
}
