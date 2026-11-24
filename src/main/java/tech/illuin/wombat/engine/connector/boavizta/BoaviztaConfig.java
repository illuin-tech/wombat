package tech.illuin.wombat.engine.connector.boavizta;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import jakarta.enterprise.inject.Produces;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.core.module.ModuleSettings;
import tech.illuin.wombat.module.boavizta.BoaviztaSettings;

/**
 * The settings of the Boavizta module, still read from {@code wombat.connector.boavizta} which deployments set.
 */
@ApplicationScoped
public class BoaviztaConfig
{
    @Produces @Singleton
    public ModuleSettings provideBoaviztaSettings(WombatProperties wombat)
    {
        BoaviztaProperties properties = wombat.connector().boavizta();
        return new BoaviztaSettings(properties.uri(), properties.proxyUrl().orElse(null));
    }
}
