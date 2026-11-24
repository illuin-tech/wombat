package tech.illuin.wombat.engine.module;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.inject.Singleton;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.core.context.WombatContextProvider;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.evaluation.impact.kubernetes.KubernetesMetricResolver;
import tech.illuin.wombat.core.evaluation.impact.llm.LLMMetricResolver;
import tech.illuin.wombat.core.module.MetricResolvers;
import tech.illuin.wombat.core.module.ModuleSettings;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.module.WombatServices;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.core.source.persistence.WombatMetricPersister;
import tech.illuin.wombat.engine.module.runtime.CoreDeploymentFactory;
import tech.illuin.wombat.engine.module.runtime.CoreRuntime;
import tech.illuin.wombat.engine.module.runtime.RuntimeAssetEvaluator;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Wires the running core: the factory assembling a deployment out of the app's beans, the runtime swapping deployments
 * on redeploy, and the evaluator following the running one. Which deployment runs is decided by
 * {@link tech.illuin.wombat.engine.module.runtime.CoreDeploymentService}.
 */
@ApplicationScoped
public class WombatCoreConfig
{
    /* Leaves the calls that already hold a replaced deployment time to complete before it is closed */
    private static final Duration RETIRED_DEPLOYMENT_GRACE_DELAY = Duration.ofSeconds(60);

    /**
     * The services handed to the modules: the secrets they read credentials from, the metric store sourced metrics are
     * read back from, and their own settings.
     */
    @ApplicationScoped
    public CoreDeploymentFactory provideCoreDeploymentFactory(
        WombatContextProvider contextProvider,
        SecretResolver secrets,
        KubernetesMetricResolver kubernetesMetrics,
        LLMMetricResolver llmMetrics,
        @All List<ModuleSettings> settings,
        WombatMetricPersister persister,
        List<WombatModule> modules,
        WombatProperties wombat
    ) {
        return new CoreDeploymentFactory(
            contextProvider,
            new WombatServices(secrets, new MetricResolvers(kubernetesMetrics, llmMetrics), settings),
            persister,
            modules,
            wombat.evaluator().cache()
        );
    }

    @ApplicationScoped
    public CoreRuntime provideCoreRuntime(CoreDeploymentFactory factory)
    {
        return new CoreRuntime(factory, CompletableFuture.delayedExecutor(RETIRED_DEPLOYMENT_GRACE_DELAY.toSeconds(), TimeUnit.SECONDS));
    }

    public void closeCoreRuntime(@Disposes CoreRuntime runtime)
    {
        runtime.close();
    }

    /**
     * Delegates to the current deployment on every call, so that its holders follow redeploys.
     */
    @Singleton
    public AssetEvaluator provideAssetEvaluator(CoreRuntime runtime)
    {
        return new RuntimeAssetEvaluator(runtime);
    }
}
