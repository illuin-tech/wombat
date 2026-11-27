package tech.illuin.wombat.persistence.backend.push;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.engine.monitor.MonitoredEnvironments;
import tech.illuin.wombat.persistence.backend.api.Action;

import java.util.Optional;
import java.util.function.Supplier;

public class EnvironmentDeclaration implements Action
{
    private final WombatEnvironmentClient client;
    private final String worker;
    private final Supplier<Optional<MonitoredEnvironments>> environments;

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentDeclaration.class);

    /**
     * @param environments what the worker declares, empty while it has nothing to declare yet: the aggregator then keeps
     *                     its previous declaration
     */
    public EnvironmentDeclaration(WombatEnvironmentClient client, String worker, Supplier<Optional<MonitoredEnvironments>> environments)
    {
        this.client = client;
        this.worker = worker;
        this.environments = environments;
    }

    @Override
    public void run()
    {
        this.environments.get().ifPresentOrElse(
            this::declare,
            () -> logger.debug("No environment declared to worker {} yet, nothing declared to the aggregator", this.worker)
        );
    }

    private void declare(MonitoredEnvironments environments)
    {
        try {
            this.client.declare(this.worker, environments);
            logger.debug("Declared environments {} to the aggregator as worker {}", environments.environments().keySet(), this.worker);
        }
        catch (FeignException e) {
            logger.warn("Failed to declare environments to the aggregator as worker {}, retrying with the next run: {}", this.worker, e.getMessage());
        }
    }
}
