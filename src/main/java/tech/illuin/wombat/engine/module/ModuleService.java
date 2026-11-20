package tech.illuin.wombat.engine.module;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.module.persistence.CoreDeploymentEntity;
import tech.illuin.wombat.engine.module.persistence.CoreInstanceEntity;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tech.illuin.wombat.engine.module.registry.UnknownModuleException;
import tech.illuin.wombat.engine.module.runtime.CoreDeploymentService;
import tech.illuin.wombat.engine.module.runtime.CoreDeploymentService.Redeploy;
import tech.illuin.wombat.engine.module.runtime.CoreRuntime;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The modules administration view, for {@code ModuleController} only: the rest of the app reads the modules from the
 * {@link ModuleRegistry}. Adds to the registry's state what the core deployments know, and the asset counts.
 */
@ApplicationScoped
public class ModuleService
{
    private final ModuleRegistry registry;
    private final AssetRepository assets;
    private final CoreRuntime runtime;
    private final CoreDeploymentService deployments;

    private static final Comparator<ModuleInfo> ORDER = Comparator.comparing(ModuleInfo::missing)
        .thenComparing(module -> module.type().name());

    public ModuleService(ModuleRegistry registry, AssetRepository assets, CoreRuntime runtime, CoreDeploymentService deployments)
    {
        this.registry = registry;
        this.assets = assets;
        this.runtime = runtime;
        this.deployments = deployments;
    }

    @Transactional
    public List<ModuleInfo> list()
    {
        Map<String, Long> counts = this.assets.countVisibleByType();
        Set<AssetType> deployed = this.runtime.current().types();
        Set<String> latest = this.latest().enabledModules;
        return this.registry.listModules().stream()
            .map(module -> info(module, deployed, latest, counts))
            .sorted(ORDER)
            .toList();
    }

    /**
     * @throws UnknownModuleException if the module is not registered
     */
    @Transactional
    public ModuleInfo find(String name)
    {
        return this.registry.getModule(name)
            .map(module -> info(module, this.runtime.current().types(), this.latest().enabledModules, this.assets.countVisibleByType()))
            .orElseThrow(() -> new UnknownModuleException(name));
    }

    /**
     * See {@link ModuleRegistry#setEnabled}: the asset pickers follow at once, the core only on redeploy.
     */
    @Transactional
    public ModuleInfo setEnabled(String name, boolean enabled, String user)
    {
        this.registry.setEnabled(name, enabled, user);
        return this.find(name);
    }

    @Transactional
    public void remove(String name)
    {
        this.registry.remove(name);
    }

    @Transactional
    public DeploymentInfo deployment()
    {
        CoreDeploymentEntity latest = this.latest();
        List<DeploymentInfo.Instance> instances = this.deployments.liveInstances().stream()
            .map(this::instance)
            .toList();
        List<DeploymentInfo.PendingModule> pending = this.registry.listModules().stream()
            .filter(module -> isPending(module, latest.enabledModules))
            .map(module -> new DeploymentInfo.PendingModule(module.type().name(), module.enabled()))
            .sorted(Comparator.comparing(DeploymentInfo.PendingModule::type))
            .toList();

        return new DeploymentInfo(
            new DeploymentInfo.Latest(latest.version, latest.id, latest.requestedAt, latest.requestedBy, latest.enabledModules),
            instances,
            pending,
            this.deployments.pollInterval().toSeconds()
        );
    }

    /**
     * See {@link CoreDeploymentService#redeploy}.
     *
     * @throws tech.illuin.wombat.engine.module.runtime.DeploymentConflictException if another instance requested a
     *         redeploy at the same time
     */
    public DeploymentInfo.Redeployed redeploy(String user)
    {
        Redeploy redeploy = this.deployments.redeploy(user);
        return new DeploymentInfo.Redeployed(redeploy.deployment().version, redeploy.deployment().id, redeploy.local());
    }

    private CoreDeploymentEntity latest()
    {
        return this.deployments.latest()
            .orElseThrow(() -> new IllegalStateException("No core deployment is recorded, the startup records one"));
    }

    private DeploymentInfo.Instance instance(CoreInstanceEntity entity)
    {
        return new DeploymentInfo.Instance(
            entity.instanceId,
            entity.deployedVersion,
            this.deployments.find(entity.deployedVersion).map(deployment -> deployment.id).orElse(null),
            entity.deployedAt,
            entity.status,
            entity.error,
            entity.detectedModules,
            entity.missingModules,
            entity.instanceId.equals(this.deployments.instanceId())
        );
    }

    /* A redeploy cannot bring a missing module in */
    private static boolean isPending(ModuleMetadata module, Set<String> latest)
    {
        return !module.missing() && module.enabled() != latest.contains(module.type().name());
    }

    private static ModuleInfo info(ModuleMetadata module, Set<AssetType> deployedTypes, Set<String> latest, Map<String, Long> counts)
    {
        return new ModuleInfo(
            module.type(),
            module.assetClass(),
            module.enabled(),
            module.creatable(),
            deployedTypes.contains(module.type()),
            isPending(module, latest),
            module.missing(),
            module.source(),
            counts.getOrDefault(module.type().name(), 0L)
        );
    }
}
