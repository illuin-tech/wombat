package tech.illuin.wombat.connector;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.mockito.Mockito;
import tech.illuin.wombat.core.connector.boavizta.connector.BoaviztaClient;
import tech.illuin.wombat.core.connector.ecologits.connector.EcologitsClient;

/**
 * Supplies the connector clients as Mockito mocks for {@code @QuarkusTest}.
 * <p>
 * They used to be MicroProfile rest-clients — normal-scoped, so {@code @InjectMock} could proxy them. They are now
 * {@code @Singleton} Feign beans produced by BoaviztaConfig/EcologitsConfig, and {@code @InjectMock} rejects
 * pseudo-scopes. A globally-enabled alternative replaces them for tests instead, which keeps the project's
 * {@code @Singleton} producer convention in main code and guarantees no test ever reaches a real endpoint.
 */
@ApplicationScoped
public class ConnectorClientMocks
{
    @Produces
    @Singleton
    @Alternative
    @Priority(1)
    public BoaviztaClient boaviztaClient()
    {
        return Mockito.mock(BoaviztaClient.class);
    }

    @Produces
    @Singleton
    @Alternative
    @Priority(1)
    public EcologitsClient ecologitsClient()
    {
        return Mockito.mock(EcologitsClient.class);
    }
}
