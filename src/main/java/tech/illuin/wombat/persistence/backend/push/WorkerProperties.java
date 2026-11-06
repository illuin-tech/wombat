package tech.illuin.wombat.persistence.backend.push;

import java.util.Optional;

public interface WorkerProperties
{
    Optional<String> id();

    Optional<AggregatorProperties> aggregator();
}
