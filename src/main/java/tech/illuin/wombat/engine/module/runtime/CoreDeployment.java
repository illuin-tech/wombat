package tech.illuin.wombat.engine.module.runtime;

import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.source.AssetMonitor;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The monitor and evaluator built out of one set of modules, swapped as a whole by {@link CoreRuntime}.
 *
 * @param modules           the module instances wired in, shared with the other deployments
 * @param missing           the requested asset-types that no module detected on this instance provides
 * @param evaluator         the evaluator the app serves, cached when the evaluator cache is enabled
 * @param uncachedEvaluator the evaluator underneath the cache, or {@code evaluator} itself when the cache is disabled
 */
public record CoreDeployment(
    List<WombatModule> modules,
    Set<String> missing,
    AssetMonitor monitor,
    AssetEvaluator evaluator,
    AssetEvaluator uncachedEvaluator,
    Instant deployedAt
) {
    public CoreDeployment
    {
        modules = List.copyOf(modules);
        missing = Set.copyOf(missing);
    }

    public Set<AssetType> types()
    {
        return this.modules.stream()
            .map(WombatModule::type)
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Closes the monitor and the evaluator, which own the components and executors; never the modules, which the other
     * deployments share. Both wait for the work already submitted to their executor.
     */
    void close()
    {
        this.monitor.close();
        /* A cached evaluator closes its delegate, the uncached one */
        this.evaluator.close();
    }
}
