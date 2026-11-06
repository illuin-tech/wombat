package tech.illuin.wombat.engine.connector.boavizta;

import io.smallrye.config.ConfigMapping;

import java.util.Optional;

@ConfigMapping(prefix = "connector.boavizta")
public interface BoaviztaProperties
{
    String uri();

    Optional<String> proxyUrl();
}
