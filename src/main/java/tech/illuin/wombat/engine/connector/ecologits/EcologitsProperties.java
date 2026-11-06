package tech.illuin.wombat.engine.connector.ecologits;

import io.smallrye.config.ConfigMapping;

import java.util.Optional;

@ConfigMapping(prefix = "connector.ecologits")
public interface EcologitsProperties
{
    String uri();

    Optional<String> proxyUrl();
}
