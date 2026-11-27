package tech.illuin.wombat.engine.context;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.commons.NodeMode;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.core.source.Monitorable;
import tech.illuin.wombat.engine.context.asset.AssetValidator;
import tech.illuin.wombat.engine.context.asset.InvalidAssetException;
import tech.illuin.wombat.engine.monitor.MonitoredEnvironments;
import tech.illuin.wombat.engine.monitor.RequiredSecretsCheck;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The environments a worker samples and declares to its aggregator: those of its monitored-environments file, reconciled
 * at startup by the {@link EnvironmentReconciler}, and, with {@code wombat.worker.environments.api}, those last received
 * on {@code PUT /api/worker/environments}.
 * <p>
 * Each declaration replaces the previous one, and only touches its own assets ({@link EnvironmentReconciler#WORKER_API_OWNER}):
 * the file's are left as they are, and an asset id declared by both is a conflict. Both may add assets to the same
 * environment. Until the first declaration, the worker samples its file but declares nothing to the aggregator, so that
 * a restart does not delete the declared assets there.
 */
@ApplicationScoped
public class WorkerEnvironmentService
{
    private static final Logger logger = LoggerFactory.getLogger(WorkerEnvironmentService.class);

    private final boolean api;
    private final MonitoredEnvironments file;
    private final EnvironmentReconciler reconciler;
    private final AssetValidator validator;
    private final RequiredSecretsCheck secrets;
    private final AssetMonitor monitor;
    private final AssetEvaluator evaluator;

    private volatile MonitoredEnvironments declared;

    public WorkerEnvironmentService(
        WombatProperties wombat,
        MonitoredEnvironments file,
        EnvironmentReconciler reconciler,
        AssetValidator validator,
        RequiredSecretsCheck secrets,
        AssetMonitor monitor,
        AssetEvaluator evaluator
    ) {
        this.api = wombat.mode() == NodeMode.worker && wombat.worker().environments().api();
        this.file = file;
        this.reconciler = reconciler;
        this.validator = validator;
        this.secrets = secrets;
        this.monitor = monitor;
        this.evaluator = evaluator;
    }

    void onStart(@Observes StartupEvent event)
    {
        if (this.api)
            logger.info("Worker environments are also declared on PUT /api/worker/environments: nothing is declared to the aggregator until the first declaration");
    }

    /**
     * What the worker declares to its aggregator: its file, merged with the last declaration when its environments are
     * also declared through its API. Empty until that first declaration.
     */
    public Optional<MonitoredEnvironments> current()
    {
        if (!this.api)
            return Optional.of(this.file);
        return Optional.ofNullable(this.declared).map(declared -> merge(this.file, declared));
    }

    /** The last declaration received through the API, empty until the first one. */
    public Optional<MonitoredEnvironments> declared()
    {
        return Optional.ofNullable(this.declared);
    }

    /**
     * Replaces the environments declared through the API: the assets it no longer declares are deleted, the others
     * created or updated, and the monitor samples them from its next heartbeat. The file's assets are left as they are.
     *
     * @throws InvalidAssetException when an asset cannot be sampled here: unknown type, not monitorable, or missing a
     *                               secret. Nothing from the declaration is applied then.
     */
    public synchronized EnvironmentReconciler.Result declare(MonitoredEnvironments environments)
    {
        if (!this.api)
            throw new IllegalStateException("Only a worker with wombat.worker.environments.api=true is declared environments through its API");

        for (Asset asset : environments.allAssets())
            this.validate(asset);
        Set<String> missing = this.secrets.missingSecrets(environments.allAssets());
        if (!missing.isEmpty())
            throw new InvalidAssetException("Missing required secret(s): " + String.join(", ", missing) + ". Configure each missing key in an available secret source of the worker");

        List<AssetIdentity> changed = new ArrayList<>();
        EnvironmentReconciler.Result result = this.reconciler.reconcile(EnvironmentReconciler.WORKER_API_OWNER, environments.environments(), changed::add);
        /* The reconciliation is committed: components built from the previous assets can be discarded */
        for (AssetIdentity identity : changed)
        {
            this.monitor.refresh(identity);
            this.evaluator.refresh(identity);
        }
        this.declared = environments;

        logger.info("Declared environments {}: {} created, {} updated, {} deleted assets", environments.environments().keySet(), result.created(), result.updated(), result.deleted());
        return result;
    }

    /** A worker only declares to its aggregator what it samples, see the aggregator's {@code WorkerController}. */
    private void validate(Asset asset)
    {
        this.validator.validate(asset);
        if (!(asset instanceof Monitorable))
            throw new InvalidAssetException("Asset " + asset.identity().id() + " of type " + asset.type().name() + " is not monitorable, a worker only declares what it samples");
    }

    /** An environment declared by both holds the assets of both: their ids cannot clash, the reconciliation refuses it. */
    static MonitoredEnvironments merge(MonitoredEnvironments file, MonitoredEnvironments declared)
    {
        Map<String, Environment> merged = new LinkedHashMap<>(file.environments());
        declared.environments().forEach((id, environment) -> merged.merge(
            id, environment, (fromFile, fromApi) -> new Environment(fromFile.id(), concat(fromFile.assets(), fromApi.assets()))
        ));
        return new MonitoredEnvironments(merged);
    }

    private static List<Asset> concat(List<Asset> first, List<Asset> second)
    {
        List<Asset> assets = new ArrayList<>(first);
        assets.addAll(second);
        return assets;
    }
}
