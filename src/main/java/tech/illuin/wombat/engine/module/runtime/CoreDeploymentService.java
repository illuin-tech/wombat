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
import java.util.ArrayList;
import java.util.Comparator;
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
    private final boolean autoDeployExtensions;
    /* The latest version that failed to build here: the poll does not retry it, a newer one is needed */
    private volatile Failure failure;

    /* After the modules are registered and the environments reconciled, before the monitor is scheduled */
    static final int STARTUP_PRIORITY_DEPLOY = 2100;
    /* A stable sort, so that the family modules of a family keep the registry's order */
    private static final Comparator<ModuleMetadata> DEPLOYMENT_ORDER = Comparator.comparing(ModuleMetadata::kind)
        .thenComparing(module -> module.type().family())
        .thenComparing(module -> module.kind() == ModuleMetadata.Kind.asset ? module.type().name() : "");
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
            wombat.mode() != NodeMode.worker,
            wombat.module().extension().autoDeploy()
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
        boolean heartbeat,
        boolean autoDeployExtensions
    ) {
        this.runtime = runtime;
        this.registry = registry;
        this.deployments = deployments;
        this.instances = instances;
        this.detected = modules.stream().map(module -> module.type().name()).collect(Collectors.toSet());
        this.instanceId = instanceId;
        this.pollInterval = pollInterval;
        this.heartbeat = heartbeat;
        this.autoDeployExtensions = autoDeployExtensions;
    }

    /**
     * Deploys the latest deployment; the first instance to start records one out of the registry. A failure here fails
     * the startup: there is no core to keep running.
     */
    void onStart(@Observes @Priority(STARTUP_PRIORITY_DEPLOY) StartupEvent event)
    {
        CoreDeploymentEntity recorded = this.deployments.findLatest().orElseGet(this::bootstrap);
        List<String> joining = this.newModulesGoingLive().stream()
            .filter(name -> !recorded.enabledModules.contains(name))
            .toList();
        CoreDeploymentEntity latest = joining.isEmpty() ? recorded : this.join(recorded, joining);
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

    /**
     * The modules registered for the first time at this startup that go live at once, in the registry's order: those the
     * app ships always do, those of extension jars unless {@code wombat.module.extension.auto-deploy} is off. The others
     * wait for a redeploy, as a toggle does.
     */
    private List<String> newModulesGoingLive()
    {
        Set<String> newlyRegistered = this.registry.newlyRegistered();
        return this.registry.listModules().stream()
            .filter(module -> newlyRegistered.contains(module.type().name()))
            .filter(module -> this.autoDeployExtensions || module.source() != ModuleMetadata.Source.extension)
            .map(module -> module.type().name())
            .toList();
    }

    /**
     * Records a deployment adding the modules registered for the first time at this startup: a module with no row is
     * enabled, so a new one runs without waiting for a redeploy. They are appended to the latest deployment's modules
     * rather than taken in the registry's order, so that a startup applies no reorder that was not redeployed. Another
     * instance may record one at the same time: its deployment is then the one taken.
     */
    private CoreDeploymentEntity join(CoreDeploymentEntity latest, List<String> joining)
    {
        logger.info("Deploying {} newly registered module(s) along with wombat core {}: {}", joining.size(), CoreRuntime.shortId(latest.id), joining);
        List<String> modules = new ArrayList<>(latest.enabledModules);
        modules.addAll(joining);
        try {
            return this.deployments.insertNext(modules, null);
        }
        catch (PersistenceException e) {
            return this.deployments.findLatest().orElseThrow(() -> e);
        }
    }

    /* A build failure is reported rather than thrown: the running deployment is kept, and the heartbeat tells it */
    @SuppressWarnings("IllegalCatch")
    private DeployResult deployHere(CoreDeploymentEntity deployment)
    {
        Set<String> before = CoreRuntime.names(this.runtime.current().types());
        try {
            Set<String> after = CoreRuntime.names(this.runtime.deploy(deployment.version, deployment.id, deployment.enabledModules).types());
            this.failure = null;
            return DeployResult.ok(difference(after, before), difference(before, after));
        }
        catch (RuntimeException e) {
            logger.error("Could not deploy wombat core {} (#{}), keeping the running one: {}", CoreRuntime.shortId(deployment.id), deployment.version, e.getMessage(), e);
            this.failure = new Failure(deployment.version, e.getMessage());
            return DeployResult.failed(e.getMessage());
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
        entity.status = failed == null ? CoreInstanceEntity.InstanceStatus.ok : CoreInstanceEntity.InstanceStatus.failed;
        entity.error = failed == null ? null : failed.error();
        entity.detectedModules = this.detected;
        entity.missingModules = current.missing();
        entity.heartbeatAt = EnvironmentEntity.now();
        this.instances.upsert(entity);
    }

    /**
     * The modules the registry enables, as a deployment lists them: the asset-type modules first, whose order means
     * nothing, by family and name; then the family modules, by family, in the registry's order.
     */
    private List<String> enabledModules()
    {
        return this.registry.listModules().stream()
            .filter(ModuleMetadata::enabled)
            .sorted(DEPLOYMENT_ORDER)
            .map(module -> module.type().name())
            .toList();
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
    public record Redeploy(CoreDeploymentEntity deployment, DeployResult local) {}

    /**
     * @param added   the asset-types the new core holds, and the previous one did not
     * @param removed the asset-types the previous core held, and the new one does not
     */
    public record DeployResult(CoreInstanceEntity.InstanceStatus status, String error, Set<String> added, Set<String> removed)
    {
        static DeployResult ok(Set<String> added, Set<String> removed)
        {
            return new DeployResult(CoreInstanceEntity.InstanceStatus.ok, null, added, removed);
        }

        static DeployResult failed(String error)
        {
            return new DeployResult(CoreInstanceEntity.InstanceStatus.failed, error, Set.of(), Set.of());
        }
    }
}
