package tech.illuin.wombat.engine.module;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.inject.Singleton;
import tech.illuin.wombat.core.WombatCore;
import tech.illuin.wombat.core.context.WombatContextProvider;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.module.WombatServices;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.core.source.persistence.WombatMetricPersister;
import tech.illuin.wombat.engine.evaluation.CachedAssetEvaluator;
import tech.illuin.wombat.engine.evaluation.CachedAssetEvaluatorProperties;
import tech.illuin.wombat.engine.module.WombatModuleConfig.DefaultHandlers;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;

import java.util.List;

/**
 * Assembles the SDK entry point out of the beans the app provides: the context to resolve, the secrets modules read
 * credentials from, the impact-resolvers the modules draw on, the persister sourced metrics are written through, and
 * the modules {@link WombatModuleConfig} decided to wire.
 */
@ApplicationScoped
public class WombatCoreConfig
{
    @Singleton
    public WombatCore provideWombatCore(
        WombatContextProvider contextProvider,
        SecretResolver secrets,
        WombatMetricPersister persister,
        List<WombatModule> modules,
        ModuleRegistry moduleRegistry,
        @All List<DefaultHandlers> defaultHandlers
    ) {
        List<WombatModule> activeModules = modules.stream()
            .filter(m -> moduleRegistry.getModule(m.type()).map(ModuleMetadata::enabled).orElse(false))
            .toList();

        // Default handlers are stateless singletons, the same instance serves every asset of their family.
        return new WombatCore(contextProvider, new WombatServices(secrets), persister, activeModules, defaults -> defaultHandlers.forEach(
            dh -> defaults.register(
                dh.family(),
                (services, asset) -> dh.activityResolver(),
                (services, asset) -> dh.impactResolver(),
                (services, asset) -> dh.costResolver())
            )
        );
    }

    /**
     * Resolvers are built per asset on first use, so a calculator keeps serving assets added later as long as their
     * module is already wired; a brand-new module needs a fresh one.
     */
    @Singleton
    public AssetEvaluator provideImpactCalculator(WombatCore core, WombatProperties wombat)
    {
        CachedAssetEvaluatorProperties cacheProperties = wombat.evaluator().cache();
        if (cacheProperties.enabled())
            return new CachedAssetEvaluator(core.createEvaluator(), cacheProperties);
        return core.createEvaluator();
    }

    public void closeWombatCore(@Disposes WombatCore core)
    {
        core.close();
    }
}
