package tech.illuin.wombat.persistence.backend.api.action;

import org.flywaydb.core.Flyway;
import tech.illuin.wombat.persistence.backend.api.Action;

public class FlywayMigrate implements Action
{
    private final Flyway flyway;

    public FlywayMigrate(Flyway flyway)
    {
        this.flyway = flyway;
    }

    @Override
    public void run()
    {
        this.flyway.migrate();
    }
}
