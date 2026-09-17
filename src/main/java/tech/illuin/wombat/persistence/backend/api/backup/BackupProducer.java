package tech.illuin.wombat.persistence.backend.api.backup;

public interface BackupProducer
{
    /**
     * Backup current datastore state
     */
    void backup();
}
