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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final Map<String, WombatModule> modules;
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
        this.modules = modules.stream().collect(Collectors.toMap(
            module -> module.type().name(),
            Function.identity()
        ));
        this.cacheProperties = cacheProperties;
    }

    /**
     * @param requested the names of the modules to deploy, in the order the core is built in, which is the order the
     *                  family modules of a family are tried in; those not detected here are reported as missing
     */
    public CoreDeployment create(long version, String id, List<String> requested)
    {
        List<WombatModule> deployed = requested.stream()
            .map(this.modules::get)
            .filter(Objects::nonNull)
            .toList();

        Set<String> missing = requested.stream()
            .filter(name -> !this.modules.containsKey(name))
            .collect(Collectors.toSet());

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
