package tech.illuin.wombat.connector.ecologits;

import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Produces;
import tech.illuin.wombat.core.connector.ecologits.connector.EcologitsClient;

@ApplicationScoped
public class EcologitsConfig
{
    @Produces @Singleton
    public EcologitsClient provideEcologitsClient(EcologitsProperties properties)
    {
        return Feign.builder()
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .target(EcologitsClient.class, properties.uri());
    }
}
