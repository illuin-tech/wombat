package tech.illuin.wombat.persistence.backend.push;

import java.util.Optional;

/**
 * Settings of the {@code worker} mode, under {@code wombat.worker}, required in that mode only.
 */
public interface WorkerProperties
{
    /**
     * Identifies this worker to its aggregator, which records it as the owner of the assets it declares. Must be unique
     * among the workers of an aggregator, and only made of letters, digits, '.', '_' and '-'.
     */
    Optional<String> id();

    Optional<AggregatorProperties> aggregator();
}
