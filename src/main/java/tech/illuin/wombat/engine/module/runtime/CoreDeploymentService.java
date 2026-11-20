package tech.illuin.wombat.engine.module.runtime;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.commons.NodeMode;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentEntity;
import tech.illuin.wombat.engine.module.persistence.CoreDeploymentEntity;
import tech.illuin.wombat.engine.module.persistence.CoreDeploymentRepository;
import tech.illuin.wombat.engine.module.persistence.CoreInstanceEntity;
import tech.illuin.wombat.engine.module.persistence.CoreInstanceRepository;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides which deployment the {@link CoreRuntime} runs, the database being the only channel between instances. A
 * redeploy records the modules enabled in the registry under the next version; every instance deploys the latest
 * version, at startup and on each poll, and writes a heartbeat telling the deployment it runs.
 */
@ApplicationScoped
public class CoreDeploymentService
{
    private final CoreRuntime runtime;
    private final ModuleRegistry registry;
    private final CoreDeploymentRepository deployments;
    private final CoreInstanceRepository instances;
    private final Set<String> detected;
    private final String instanceId;
    private final Duration pollInterval;
    private final boolean heartbeat;
    /* The latest version that failed to build here: the poll does not retry it, a newer one is needed */
    private volatile Failure failure;

    /* After the modules are registered and the environments reconciled, before the monitor is scheduled */
    static final int STARTUP_PRIORITY_DEPLOY = 2100;
    private static final Logger logger = LoggerFactory.getLogger(CoreDeploymentService.class);

    @Inject
    public CoreDeploymentService(
        CoreRuntime runtime,
        ModuleRegistry registry,
        CoreDeploymentRepository deployments,
        CoreInstanceRepository instances,
        List<WombatModule> modules,
        WombatProperties wombat
    ) {
        this(
            runtime,
            registry,
            deployments,
            instances,
            modules,
            wombat.module().deployment().instanceId(),
            wombat.module().deployment().pollInterval(),
            /* A worker has no API nor UI to show its heartbeat, nor replicas to converge with */
            wombat.mode() != NodeMode.worker
        );
    }

    CoreDeploymentService(
        CoreRuntime runtime,
        ModuleRegistry registry,
        CoreDeploymentRepository deployments,
        CoreInstanceRepository instances,
        List<WombatModule> modules,
        String instanceId,
        Duration pollInterval,
        boolean heartbeat
    ) {
        this.runtime = runtime;
        this.registry = registry;
        this.deployments = deployments;
        this.instances = instances;
        this.detected = modules.stream().map(module -> module.type().name()).collect(Collectors.toSet());
        this.instanceId = instanceId;
        this.pollInterval = pollInterval;
        this.heartbeat = heartbeat;
    }

    /**
     * Deploys the latest deployment; the first instance to start records one out of the registry. A failure here fails
     * the startup: there is no core to keep running.
     */
    void onStart(@Observes @Priority(STARTUP_PRIORITY_DEPLOY) StartupEvent event)
    {
        CoreDeploymentEntity latest = this.deployments.findLatest().orElseGet(this::bootstrap);
        this.runtime.deploy(latest.version, latest.id, latest.enabledModules);
        this.writeHeartbeat();
    }

    /**
     * Records a deployment of the modules enabled in the registry, then deploys it here; the other instances follow on
     * their next poll.
     *
     * @param user who requested it, null when authentication is disabled
     * @throws DeploymentConflictException if another instance recorded a redeploy at the same time
     */
    public Redeploy redeploy(String user)
    {
        try {
            CoreDeploymentEntity next = this.deployments.insertNext(this.enabledModules(), user);
            return new Redeploy(next, this.deployHere(next));
        }
        catch (PersistenceException e) {
            throw new DeploymentConflictException("Another redeploy was just requested, the instances converge on it", e);
        }
    }

    @Scheduled(
        identity = "wombat-core-deployment-sync",
        every = "${wombat.module.deployment.poll-interval:30s}",
        delayed = "${wombat.module.deployment.poll-interval:30s}",
        concurrentExecution = Scheduled.ConcurrentExecution.SKIP
    )
    void sync()
    {
        if (!this.heartbeat)
            return;
        Failure failed = this.failure;
        this.deployments.findLatest()
            .filter(latest -> latest.version > this.runtime.current().version())
            .filter(latest -> failed == null || latest.version > failed.version())
            .ifPresent(this::deployHere);
        this.writeHeartbeat();
    }

    public Optional<CoreDeploymentEntity> latest()
    {
        return this.deployments.findLatest();
    }

    public Optional<CoreDeploymentEntity> find(long version)
    {
        return this.deployments.findByIdOptional(version);
    }

    public List<CoreInstanceEntity> liveInstances()
    {
        return this.instances.findLive(this.pollInterval);
    }

    public String instanceId()
    {
        return this.instanceId;
    }

    public Duration pollInterval()
    {
        return this.pollInterval;
    }

    /**
     * The first instance to start records the modules the registry enables; another one starting at the same time may
     * win that race, its deployment is then the one taken.
     */
    private CoreDeploymentEntity bootstrap()
    {
        try {
            return this.deployments.insertNext(this.enabledModules(), null);
        }
        catch (PersistenceException e) {
            return this.deployments.findLatest().orElseThrow(() -> e);
        }
    }

    /* A build failure is reported rather than thrown: the running deployment is kept, and the heartbeat tells it */
    @SuppressWarnings("IllegalCatch")
    private Result deployHere(CoreDeploymentEntity deployment)
    {
        Set<String> before = CoreRuntime.names(this.runtime.current().types());
        try {
            Set<String> after = CoreRuntime.names(this.runtime.deploy(deployment.version, deployment.id, deployment.enabledModules).types());
            this.failure = null;
            return Result.ok(difference(after, before), difference(before, after));
        }
        catch (RuntimeException e) {
            logger.error("Could not deploy wombat core {} (#{}), keeping the running one: {}", CoreRuntime.shortId(deployment.id), deployment.version, e.getMessage(), e);
            this.failure = new Failure(deployment.version, e.getMessage());
            return Result.failed(e.getMessage());
        }
        finally {
            this.writeHeartbeat();
        }
    }

    private void writeHeartbeat()
    {
        if (!this.heartbeat)
            return;
        CoreDeployment current = this.runtime.current();
        Failure failed = this.failure;

        CoreInstanceEntity entity = new CoreInstanceEntity();
        entity.instanceId = this.instanceId;
        entity.deployedVersion = current.version();
        entity.deployedAt = current.deployedAt();
        entity.status = failed == null ? CoreInstanceEntity.Status.ok : CoreInstanceEntity.Status.failed;
        entity.error = failed == null ? null : failed.error();
        entity.detectedModules = this.detected;
        entity.missingModules = current.missing();
        entity.heartbeatAt = EnvironmentEntity.now();
        this.instances.upsert(entity);
    }

    private Set<String> enabledModules()
    {
        return this.registry.listModules().stream()
            .filter(ModuleMetadata::enabled)
            .map(module -> module.type().name())
            .collect(Collectors.toSet());
    }

    private static Set<String> difference(Set<String> from, Set<String> without)
    {
        Set<String> difference = new HashSet<>(from);
        difference.removeAll(without);
        return difference;
    }

    private record Failure(long version, String error) {}

    /**
     * A redeploy recorded, and the outcome of deploying it on the instance which recorded it.
     */
    public record Redeploy(CoreDeploymentEntity deployment, Result local) {}

    /**
     * @param added   the asset-types the new core holds, and the previous one did not
     * @param removed the asset-types the previous core held, and the new one does not
     */
    public record Result(CoreInstanceEntity.Status status, String error, Set<String> added, Set<String> removed)
    {
        static Result ok(Set<String> added, Set<String> removed)
        {
            return new Result(CoreInstanceEntity.Status.ok, null, added, removed);
        }

        static Result failed(String error)
        {
            return new Result(CoreInstanceEntity.Status.failed, error, Set.of(), Set.of());
        }
    }
}
