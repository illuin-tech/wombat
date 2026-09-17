package tech.illuin.wombat.connector.ecologits;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "connector.ecologits")
public interface EcologitsProperties
{
    String uri();
}
