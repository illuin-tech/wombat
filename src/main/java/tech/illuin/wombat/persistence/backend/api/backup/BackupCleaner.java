package tech.illuin.wombat.persistence.backend.api.backup;

public interface BackupCleaner
{
    /**
     * Cleanup old backups
     */
    void clean();
}
