package tech.illuin.wombat.engine.module;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.inject.Singleton;
import tech.illuin.wombat.core.WombatCore;
import tech.illuin.wombat.core.context.WombatContextProvider;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.source.persistence.WombatMetricPersister;
import tech.illuin.wombat.engine.evaluation.CachedAssetEvaluator;
import tech.illuin.wombat.engine.evaluation.CachedAssetEvaluatorProperties;
import tech.illuin.wombat.engine.module.WombatModuleConfig.DefaultHandlers;

import java.util.List;

/**
 * Assembles the SDK entry point out of the beans the app provides: the context to resolve, the impact-resolvers the
 * modules draw on, the persister sourced metrics are written through, and the modules {@link WombatModuleConfig}
 * decided to wire.
 */
@ApplicationScoped
public class WombatCoreConfig
{
    @Singleton
    public WombatCore provideWombatCore(
        WombatContextProvider contextProvider,
        WombatMetricPersister persister,
        List<WombatModule> modules,
        @All List<DefaultHandlers> defaultHandlers
    ) {
        return new WombatCore(contextProvider, persister, modules, defaults -> defaultHandlers.forEach(
            dh -> defaults.register(
                dh.family(),
                dh.activityResolver(),
                dh.impactResolver(),
                dh.costResolver())
            )
        );
    }

    /**
     * Resolvers are registered per asset-type at creation, so a calculator keeps serving assets added later as long
     * as their type is already wired; a brand-new asset-type needs a fresh one.
     */
    @Singleton
    public AssetEvaluator provideImpactCalculator(WombatCore core, CachedAssetEvaluatorProperties cacheProperties)
    {
        if (cacheProperties.enabled())
            return new CachedAssetEvaluator(core.createEvaluator(), cacheProperties);
        return core.createEvaluator();
    }

    public void closeWombatCore(@Disposes WombatCore core)
    {
        core.close();
    }
}
