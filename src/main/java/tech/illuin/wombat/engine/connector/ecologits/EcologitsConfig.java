package tech.illuin.wombat.engine.connector.ecologits;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Produces;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.core.connector.ecologits.connector.EcologitsClient;
import tech.illuin.wombat.module.connector.ecologits.EcologitsClientConfig;

@ApplicationScoped
public class EcologitsConfig
{
    @Produces @Singleton
    public EcologitsClient provideEcologitsClient(WombatProperties wombat)
    {
        EcologitsProperties properties = wombat.connector().ecologits();
        return EcologitsClientConfig.create(properties.uri(), properties.proxyUrl().orElse(null));
    }
}
