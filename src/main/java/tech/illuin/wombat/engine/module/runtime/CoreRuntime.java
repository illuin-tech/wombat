package tech.illuin.wombat.engine.module.runtime;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Holds the running {@link CoreDeployment}, and swaps it for a new one on redeploy.
 * <p>
 * The app's {@code AssetMonitor} and {@code AssetEvaluator} beans read {@link #current()} on every call, see
 * {@link RuntimeAssetMonitor} and {@link RuntimeAssetEvaluator}, so they follow a swap at once. The replaced deployment
 * is closed after a grace delay, leaving the calls that already hold it time to complete.
 */
@ApplicationScoped
public class CoreRuntime
{
    private final CoreDeploymentFactory factory;
    private final ModuleRegistry registry;
    private final Executor retiredCloser;
    private volatile CoreDeployment current;

    /* After the environments are reconciled, and before the monitor is scheduled */
    static final int STARTUP_PRIORITY_DEPLOY = 2100;
    private static final Duration CLOSE_GRACE_DELAY = Duration.ofSeconds(60);
    private static final Logger logger = LoggerFactory.getLogger(CoreRuntime.class);

    @Inject
    public CoreRuntime(CoreDeploymentFactory factory, ModuleRegistry registry)
    {
        this(factory, registry, CompletableFuture.delayedExecutor(CLOSE_GRACE_DELAY.toSeconds(), TimeUnit.SECONDS));
    }

    CoreRuntime(CoreDeploymentFactory factory, ModuleRegistry registry, Executor retiredCloser)
    {
        this.factory = factory;
        this.registry = registry;
        this.retiredCloser = retiredCloser;
    }

    void onStart(@Observes @Priority(STARTUP_PRIORITY_DEPLOY) StartupEvent event)
    {
        this.deploy(this.registry.listModules().stream()
            .filter(ModuleMetadata::enabled)
            .map(module -> module.type().name())
            .collect(Collectors.toSet())
        );
    }

    /**
     * @throws IllegalStateException before the first deployment
     */
    public CoreDeployment current()
    {
        CoreDeployment deployment = this.current;
        if (deployment == null)
            throw new IllegalStateException("The wombat core is not deployed yet");
        return deployment;
    }

    /**
     * Builds a deployment of the requested asset-types and swaps it in. If the build fails, the running deployment is
     * kept and the exception is thrown. Deployments are applied one at a time.
     */
    public synchronized CoreDeployment deploy(Set<String> requested)
    {
        CoreDeployment next = this.factory.create(requested);
        CoreDeployment previous = this.current;
        this.current = next;

        if (previous != null)
            this.retiredCloser.execute(previous::close);
        this.logChanges(previous, next);
        return next;
    }

    /* Synchronized with deploy, so that a deployment swapped in during shutdown is the one closed */
    @PreDestroy
    synchronized void close()
    {
        CoreDeployment deployment = this.current;
        if (deployment != null)
            deployment.close();
    }

    private void logChanges(CoreDeployment previous, CoreDeployment next)
    {
        Set<String> before = previous == null ? Set.of() : names(previous.types());
        Set<String> after = names(next.types());

        Set<String> added = new HashSet<>(after);
        added.removeAll(before);
        Set<String> removed = new HashSet<>(before);
        removed.removeAll(after);

        logger.info("Deployed wombat core with {} module(s), added {}, removed {}", after.size(), added, removed);
        if (!next.missing().isEmpty())
            logger.warn("Requested asset-types with no module on this instance: {}", next.missing());
    }

    private static Set<String> names(Set<AssetType> types)
    {
        return types.stream()
            .map(AssetType::name)
            .collect(Collectors.toSet());
    }
}
