package tech.illuin.wombat.connector.boavizta;

import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Produces;
import tech.illuin.wombat.core.connector.boavizta.connector.BoaviztaClient;
import tech.illuin.wombat.core.connector.boavizta.impact.BoaviztaEvaluationResolver;

@ApplicationScoped
public class BoaviztaConfig
{
    @Produces @Singleton
    public BoaviztaClient provideBoaviztaClient(BoaviztaProperties properties)
    {
        return Feign.builder()
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .target(BoaviztaClient.class, properties.uri());
    }

    @Produces @Singleton
    public BoaviztaEvaluationResolver provideBoaviztaLoadImpactResolver(BoaviztaClient boaviztaClient)
    {
        return new BoaviztaEvaluationResolver(boaviztaClient);
    }
}
