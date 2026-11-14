package tech.illuin.wombat.engine.connector.ecologits;

import java.util.Optional;

public interface EcologitsProperties
{
    String uri();

    Optional<String> proxyUrl();
}
