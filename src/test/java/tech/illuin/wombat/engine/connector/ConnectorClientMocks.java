package tech.illuin.wombat.engine.connector;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.mockito.Mockito;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.core.evaluation.WombatEvaluationResolver;
import tech.illuin.wombat.core.module.ProcessorFactory;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.engine.module.WombatModuleConfig;
import tech.illuin.wombat.engine.module.extension.ExtensionLoader;
import tech.illuin.wombat.module.boavizta.BoaviztaModule;
import tech.illuin.wombat.module.boavizta.connector.BoaviztaClient;
import tech.illuin.wombat.module.boavizta.impact.BoaviztaEvaluationResolver;
import tech.illuin.wombat.module.ecologits.EcologitsModule;
import tech.illuin.wombat.module.ecologits.connector.EcologitsClient;
import tech.illuin.wombat.module.ecologits.impact.EcologitsEvaluationResolver;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Supplies the connector clients as Mockito mocks for {@code @QuarkusTest}, and the Boavizta and Ecologits modules
 * built on them rather than on their settings.
 * <p>
 * The modules build their own clients, out of the settings in their services: the detected modules are therefore
 * swapped for subclasses whose resolvers take the mocks, so that tests keep stubbing the clients they inject.
 * <p>
 * The clients are no longer beans of the app, so {@code @InjectMock} has nothing to replace: these globally-enabled
 * alternatives produce them as {@code @Singleton} mocks instead, which also guarantees no test ever reaches a real
 * endpoint. The settings the real modules would read are checked by {@link ConnectorSettingsTest}.
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

    @Produces
    @Singleton
    @Alternative
    @Priority(1)
    public ExtensionLoader extensionLoader(WombatProperties wombat, BoaviztaClient boavizta, EcologitsClient ecologits)
    {
        ExtensionLoader loader = WombatModuleConfig.provideWombatExtensionLoader(wombat);
        return new ExtensionLoader()
        {
            @Override
            public List<WombatModule> load()
            {
                return loader.load().stream()
                    .map(module -> switch (module)
                    {
                        case BoaviztaModule ignored -> new MockedBoaviztaModule(boavizta);
                        case EcologitsModule ignored -> new MockedEcologitsModule(ecologits);
                        default -> module;
                    })
                    .toList();
            }

            @Override
            public void close() throws IOException
            {
                loader.close();
            }
        };
    }

    private static final class MockedBoaviztaModule extends BoaviztaModule
    {
        private final BoaviztaClient client;

        MockedBoaviztaModule(BoaviztaClient client)
        {
            this.client = client;
        }

        @Override
        public Optional<ProcessorFactory<WombatEvaluationResolver>> impactResolver()
        {
            return Optional.of((services, asset) -> new BoaviztaEvaluationResolver(this.client));
        }
    }

    private static final class MockedEcologitsModule extends EcologitsModule
    {
        private final EcologitsClient client;

        MockedEcologitsModule(EcologitsClient client)
        {
            this.client = client;
        }

        @Override
        public Optional<ProcessorFactory<WombatEvaluationResolver>> impactResolver()
        {
            return Optional.of((services, asset) -> new EcologitsEvaluationResolver(this.client));
        }
    }
}
