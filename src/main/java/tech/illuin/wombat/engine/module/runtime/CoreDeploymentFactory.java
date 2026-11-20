package tech.illuin.wombat.engine.module.runtime;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.core.WombatCore;
import tech.illuin.wombat.core.context.WombatContextProvider;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.module.WombatServices;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.core.source.persistence.WombatMetricPersister;
import tech.illuin.wombat.engine.evaluation.CachedAssetEvaluator;
import tech.illuin.wombat.engine.evaluation.CachedAssetEvaluatorProperties;
import tech.illuin.wombat.engine.module.WombatModuleConfig.DefaultHandlers;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a {@link CoreDeployment} out of the beans the app provides: the context to resolve, the secrets modules read
 * credentials from, the impact-resolvers the modules draw on, the persister sourced metrics are written through, and
 * the requested modules among those detected on this instance.
 */
@ApplicationScoped
public class CoreDeploymentFactory
{
    private final WombatContextProvider contextProvider;
    private final SecretResolver secrets;
    private final WombatMetricPersister persister;
    private final List<WombatModule> modules;
    private final List<DefaultHandlers> defaultHandlers;
    private final CachedAssetEvaluatorProperties cacheProperties;

    @Inject
    public CoreDeploymentFactory(
        WombatContextProvider contextProvider,
        SecretResolver secrets,
        WombatMetricPersister persister,
        List<WombatModule> modules,
        @All List<DefaultHandlers> defaultHandlers,
        WombatProperties wombat
    ) {
        this(contextProvider, secrets, persister, modules, defaultHandlers, wombat.evaluator().cache());
    }

    CoreDeploymentFactory(
        WombatContextProvider contextProvider,
        SecretResolver secrets,
        WombatMetricPersister persister,
        List<WombatModule> modules,
        List<DefaultHandlers> defaultHandlers,
        CachedAssetEvaluatorProperties cacheProperties
    ) {
        this.contextProvider = contextProvider;
        this.secrets = secrets;
        this.persister = persister;
        this.modules = modules;
        this.defaultHandlers = defaultHandlers;
        this.cacheProperties = cacheProperties;
    }

    /**
     * @param requested the names of the asset-types to deploy; those no detected module provides are reported as missing
     */
    public CoreDeployment create(Set<String> requested)
    {
        List<WombatModule> deployed = this.modules.stream()
            .filter(module -> requested.contains(module.type().name()))
            .toList();

        Set<String> missing = new HashSet<>(requested);
        deployed.forEach(module -> missing.remove(module.type().name()));

        /* The core holds nothing but the modules, which are closed by CoreRuntime: it is not kept, nor closed. */
        WombatCore core = this.createCore(deployed);
        AssetMonitor monitor = core.createMonitor();
        AssetEvaluator uncached = core.createEvaluator();
        AssetEvaluator evaluator = this.cacheProperties.enabled()
            ? new CachedAssetEvaluator(uncached, this.cacheProperties)
            : uncached;

        return new CoreDeployment(deployed, missing, monitor, evaluator, uncached, Instant.now());
    }

    private WombatCore createCore(List<WombatModule> deployed)
    {
        // Default handlers are stateless singletons, the same instance serves every asset of their family.
        return new WombatCore(this.contextProvider, new WombatServices(this.secrets), this.persister, deployed, defaults -> this.defaultHandlers.forEach(
            dh -> defaults.register(
                dh.family(),
                (services, asset) -> dh.activityResolver(),
                (services, asset) -> dh.impactResolver(),
                (services, asset) -> dh.costResolver())
            )
        );
    }
}
