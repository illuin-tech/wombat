package tech.illuin.wombat.persistence.backend.push;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.engine.monitor.MonitoredEnvironments;
import tech.illuin.wombat.persistence.backend.api.Action;

public class EnvironmentDeclaration implements Action
{
    private final WombatEnvironmentClient client;
    private final String worker;
    private final MonitoredEnvironments environments;

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentDeclaration.class);

    public EnvironmentDeclaration(WombatEnvironmentClient client, String worker, MonitoredEnvironments environments)
    {
        this.client = client;
        this.worker = worker;
        this.environments = environments;
    }

    @Override
    public void run()
    {
        try {
            this.client.declare(this.worker, this.environments);
            logger.debug("Declared environments {} to the aggregator as worker {}", this.environments.environments().keySet(), this.worker);
        }
        catch (FeignException e) {
            logger.warn("Failed to declare environments to the aggregator as worker {}, retrying with the next run: {}", this.worker, e.getMessage());
        }
    }
}
