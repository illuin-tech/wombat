package tech.illuin.wombat.connector.boavizta;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "connector.boavizta")
public interface BoaviztaProperties
{
    String uri();
}
