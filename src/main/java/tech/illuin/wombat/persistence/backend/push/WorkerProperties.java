package tech.illuin.wombat.persistence.backend.push;

import io.smallrye.config.WithDefault;

import java.util.Optional;

public interface WorkerProperties
{
    Optional<String> id();

    Optional<AggregatorProperties> aggregator();

    EnvironmentsProperties environments();

    interface EnvironmentsProperties
    {
        /**
         * Whether {@code PUT /api/worker/environments} also declares environments, on top of the
         * {@code wombat.monitor.environments-file}: e.g. a sidecar listing the namespaces of its cluster.
         */
        @WithDefault("false")
        boolean api();
    }
}
