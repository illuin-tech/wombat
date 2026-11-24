package tech.illuin.wombat.engine.connector.ecologits;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import jakarta.enterprise.inject.Produces;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.core.module.ModuleSettings;
import tech.illuin.wombat.module.ecologits.EcologitsSettings;

/**
 * The settings of the Ecologits module, still read from {@code wombat.connector.ecologits} which deployments set.
 */
@ApplicationScoped
public class EcologitsConfig
{
    @Produces @Singleton
    public ModuleSettings provideEcologitsSettings(WombatProperties wombat)
    {
        EcologitsProperties properties = wombat.connector().ecologits();
        return new EcologitsSettings(properties.uri(), properties.proxyUrl().orElse(null));
    }
}
