package tech.illuin.wombat.engine.connector.boavizta;

import feign.Client;
import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Produces;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.core.connector.boavizta.connector.BoaviztaClient;
import tech.illuin.wombat.core.connector.boavizta.impact.BoaviztaEvaluationResolver;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;

@ApplicationScoped
public class BoaviztaConfig
{
    @Produces @Singleton
    public BoaviztaClient provideBoaviztaClient(WombatProperties wombat)
    {
        BoaviztaProperties properties = wombat.connector().boavizta();
        Feign.Builder builder = Feign.builder()
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder());

        properties.proxyUrl().ifPresent(proxyUrl -> {
            URI proxy = URI.create(proxyUrl);
            if (proxy.getHost() == null || proxy.getPort() == -1)
                throw new IllegalArgumentException("wombat.connector.boavizta.proxy-url must include a host and port, got: " + proxyUrl);
            builder.client(new Client.Proxied(null, null, new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy.getHost(), proxy.getPort()))));
        });

        return builder.target(BoaviztaClient.class, properties.uri());
    }

    @Produces @Singleton
    public BoaviztaEvaluationResolver provideBoaviztaLoadImpactResolver(BoaviztaClient boaviztaClient)
    {
        return new BoaviztaEvaluationResolver(boaviztaClient);
    }
}
