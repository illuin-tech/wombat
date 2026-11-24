package tech.illuin.wombat.engine.module.runtime;

import tech.illuin.wombat.core.WombatCore;
import tech.illuin.wombat.core.context.WombatContextProvider;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.module.WombatServices;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.core.source.persistence.WombatMetricPersister;
import tech.illuin.wombat.engine.evaluation.CachedAssetEvaluator;
import tech.illuin.wombat.engine.evaluation.CachedAssetEvaluatorProperties;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a {@link CoreDeployment} out of the beans the app provides: the context to resolve, the services modules draw
 * on (the secrets they read credentials from, the metric store sourced metrics are read back from, and their own
 * settings), the persister sourced metrics are written through, and the requested modules among those detected on this
 * instance.
 */
public class CoreDeploymentFactory
{
    private final WombatContextProvider contextProvider;
    private final WombatServices services;
    private final WombatMetricPersister persister;
    private final List<WombatModule> modules;
    private final CachedAssetEvaluatorProperties cacheProperties;

    public CoreDeploymentFactory(
        WombatContextProvider contextProvider,
        WombatServices services,
        WombatMetricPersister persister,
        List<WombatModule> modules,
        CachedAssetEvaluatorProperties cacheProperties
    ) {
        this.contextProvider = contextProvider;
        this.services = services;
        this.persister = persister;
        this.modules = modules;
        this.cacheProperties = cacheProperties;
    }

    /**
     * @param requested the names of the modules to deploy; those not detected here are reported as missing
     */
    public CoreDeployment create(long version, String id, Set<String> requested)
    {
        List<WombatModule> deployed = this.modules.stream()
            .filter(module -> requested.contains(module.type().name()))
            .toList();

        Set<String> missing = new HashSet<>(requested);
        deployed.forEach(module -> missing.remove(module.type().name()));

        /* The core holds nothing but the modules, which every deployment shares: it is only kept to build evaluators,
           and never closed */
        WombatCore core = this.createCore(deployed);
        AssetMonitor monitor = core.createMonitor();
        AssetEvaluator uncached = core.createEvaluator();
        AssetEvaluator evaluator = this.cacheProperties.enabled()
            ? new CachedAssetEvaluator(uncached, this.cacheProperties)
            : uncached;

        return new CoreDeployment(version, id, deployed, missing, monitor, evaluator, core::createEvaluator, Instant.now());
    }

    private WombatCore createCore(List<WombatModule> deployed)
    {
        return new WombatCore(this.contextProvider, this.services, this.persister, deployed);
    }
}
