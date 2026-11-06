package tech.illuin.wombat.persistence.backend.push;

import java.time.Duration;
import java.util.Optional;

public interface AggregatorProperties
{
    /**
     * Base URL of the aggregator instance receiving the metrics, e.g. {@code https://wombat.example.com}.
     */
    String url();

    /**
     * API key holding the {@code api.metric.write} permission on the aggregator.
     */
    String apiKey();

    /**
     * HTTP proxy to reach the aggregator through, e.g. {@code http://proxy.example.com:3128}.
     */
    Optional<String> proxyUrl();

    /**
     * Connect and read timeout of each push, 30s by default. Not defaulted through the mapping, which would make the
     * whole optional group present.
     */
    Optional<Duration> timeout();
}
