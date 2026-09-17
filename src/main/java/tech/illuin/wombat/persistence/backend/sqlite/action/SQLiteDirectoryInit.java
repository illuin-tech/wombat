package tech.illuin.wombat.persistence.backend.sqlite.action;

import tech.illuin.wombat.persistence.backend.api.Action;

import java.nio.file.Path;

public class SQLiteDirectoryInit implements Action
{
    private final Path dbPath;

    public SQLiteDirectoryInit(Path dbPath)
    {
        this.dbPath = dbPath;
    }

    @Override
    public void run()
    {
        this.dbPath.getParent().toFile().mkdirs();
    }
}
