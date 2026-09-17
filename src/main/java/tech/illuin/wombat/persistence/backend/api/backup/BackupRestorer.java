package tech.illuin.wombat.persistence.backend.api.backup;

public interface BackupRestorer
{
    /**
     * Restore datastore to latest saved backup
     */
    boolean restore() throws RestoreException;

    class RestoreException extends Exception
    {
        public RestoreException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }
}
