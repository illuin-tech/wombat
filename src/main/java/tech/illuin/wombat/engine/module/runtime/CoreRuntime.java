package tech.illuin.wombat.engine.module.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.asset.type.ModuleType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Holds the running {@link CoreDeployment}, and swaps it for a new one on redeploy. Which deployment to run is decided
 * by {@link CoreDeploymentService}.
 * <p>
 * The app's {@code AssetMonitor} and {@code AssetEvaluator} beans read {@link #current()} on every call, see
 * {@link RuntimeAssetMonitor} and {@link RuntimeAssetEvaluator}, so they follow a swap at once. The replaced deployment
 * is closed through {@code retiredCloser}, which the app delays to leave the calls that already hold it time to
 * complete.
 */
public class CoreRuntime
{
    private final CoreDeploymentFactory factory;
    private final Executor retiredCloser;
    private volatile CoreDeployment current;

    private static final Logger logger = LoggerFactory.getLogger(CoreRuntime.class);

    public CoreRuntime(CoreDeploymentFactory factory, Executor retiredCloser)
    {
        this.factory = factory;
        this.retiredCloser = retiredCloser;
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
     * Builds a deployment of the requested modules, in their order, and swaps it in. If the build fails, the running
     * deployment is kept and the exception is thrown. Deployments are applied one at a time, and only forward: a version
     * not newer than the running one is ignored.
     *
     * @return the running deployment
     */
    public synchronized CoreDeployment deploy(long version, String id, List<String> requested)
    {
        CoreDeployment previous = this.current;
        if (previous != null && version <= previous.version())
        {
            logger.debug("Already running core deployment {}, ignoring deployment {}", previous.version(), version);
            return previous;
        }

        CoreDeployment next = this.factory.create(version, id, requested);
        this.current = next;

        if (previous != null)
            this.retiredCloser.execute(previous::close);
        this.logChanges(previous, next);
        return next;
    }

    /* Synchronized with deploy, so that a deployment swapped in during shutdown is the one closed */
    public synchronized void close()
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

        logger.info("Deployed wombat core {} (#{}) with {} module(s), added {}, removed {}", shortId(next.id()), next.version(), after.size(), added, removed);
        if (!next.missing().isEmpty())
            logger.warn("Requested modules not detected on this instance: {}", next.missing());
    }

    static Set<String> names(Set<ModuleType> types)
    {
        return types.stream()
            .map(ModuleType::name)
            .collect(Collectors.toSet());
    }

    /**
     * @return the first 7 characters of a deployment id, like a short commit id
     */
    public static String shortId(String id)
    {
        return id.length() > 7 ? id.substring(0, 7) : id;
    }
}
