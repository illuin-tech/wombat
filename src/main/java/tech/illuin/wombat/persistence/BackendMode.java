package tech.illuin.wombat.persistence;

public enum BackendMode
{
    sqlite
    {
        @Override
        public PersistenceBackendProperties.SQLiteProperties getSection(PersistenceBackendProperties parent)
        {
            return parent.sqlite().orElseThrow(() -> new IllegalArgumentException("sqlite properties required when mode=sqlite"));
        }
    },
    postgresql
    {
        @Override
        public PersistenceBackendProperties.PostgreSQLProperties getSection(PersistenceBackendProperties parent)
        {
            return parent.postgresql().orElseThrow(() -> new IllegalArgumentException("postgresql properties required when mode=postgresql"));
        }
    };

    public abstract <T> T getSection(PersistenceBackendProperties parent);
}
