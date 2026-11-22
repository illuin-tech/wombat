package tech.illuin.wombat.engine.connector.boavizta;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Produces;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.core.connector.boavizta.connector.BoaviztaClient;
import tech.illuin.wombat.core.connector.boavizta.impact.BoaviztaEvaluationResolver;
import tech.illuin.wombat.module.connector.boavizta.BoaviztaClientConfig;

@ApplicationScoped
public class BoaviztaConfig
{
    @Produces @Singleton
    public BoaviztaClient provideBoaviztaClient(WombatProperties wombat)
    {
        BoaviztaProperties properties = wombat.connector().boavizta();
        return BoaviztaClientConfig.create(properties.uri(), properties.proxyUrl().orElse(null));
    }

    @Produces @Singleton
    public BoaviztaEvaluationResolver provideBoaviztaLoadImpactResolver(BoaviztaClient boaviztaClient)
    {
        return new BoaviztaEvaluationResolver(boaviztaClient);
    }
}
