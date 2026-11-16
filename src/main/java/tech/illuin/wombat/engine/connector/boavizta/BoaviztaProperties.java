package tech.illuin.wombat.engine.connector.boavizta;

import java.util.Optional;

public interface BoaviztaProperties
{
    String uri();

    Optional<String> proxyUrl();
}
