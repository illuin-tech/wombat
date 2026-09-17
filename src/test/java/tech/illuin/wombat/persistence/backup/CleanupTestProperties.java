package tech.illuin.wombat.persistence.backup;

import io.soabase.recordbuilder.core.RecordBuilder;
import tech.illuin.wombat.persistence.backend.api.backup.BackupProperties;

@RecordBuilder
public record CleanupTestProperties(
    boolean enabled,
    String cron,
    int retainLast
) implements BackupProperties.CleanupProperties {}
