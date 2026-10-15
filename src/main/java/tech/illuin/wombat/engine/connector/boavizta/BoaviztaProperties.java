package tech.illuin.wombat.engine.connector.boavizta;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "connector.boavizta")
public interface BoaviztaProperties
{
    String uri();
}
