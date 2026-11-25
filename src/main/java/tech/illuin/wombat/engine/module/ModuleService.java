package tech.illuin.wombat.engine.module;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.core.asset.type.ModuleType;
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

    public ModuleService(ModuleRegistry registry, AssetRepository assets, CoreRuntime runtime, CoreDeploymentService deployments)
    {
        this.registry = registry;
        this.assets = assets;
        this.runtime = runtime;
        this.deployments = deployments;
    }

    /**
     * @return every registered module, missing ones included, in the order of the modules
     */
    @Transactional
    public List<ModuleInfo> list()
    {
        Map<String, Long> counts = this.assets.countVisibleByType();
        Set<ModuleType> deployed = this.runtime.current().types();
        List<String> latest = this.latest().enabledModules;
        return this.registry.listModules().stream()
            .sorted(ModuleMetadata.ORDERING)
            .map(module -> info(module, deployed, latest, counts))
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

    /**
     * See {@link ModuleRegistry#setOrder}: the core follows on redeploy.
     *
     * @return every registered module, in their new order
     * @throws tech.illuin.wombat.engine.module.registry.ModuleOrderException if {@code modules} does not list each
     *         registered module exactly once
     */
    @Transactional
    public List<ModuleInfo> setOrder(List<String> modules, String user)
    {
        this.registry.setOrder(modules, user);
        return this.list();
    }

    @Transactional
    public DeploymentInfo deployment()
    {
        CoreDeploymentEntity latest = this.latest();
        List<DeploymentInfo.CoreInstance> instances = this.deployments.liveInstances().stream()
            .map(this::instance)
            .toList();
        List<ModuleMetadata> modules = this.registry.listModules();
        List<DeploymentInfo.PendingModule> pending = modules.stream()
            .filter(module -> isPending(module, latest.enabledModules))
            .map(module -> new DeploymentInfo.PendingModule(module.type().name(), module.enabled()))
            .sorted(Comparator.comparing(DeploymentInfo.PendingModule::type))
            .toList();

        return new DeploymentInfo(
            new DeploymentInfo.LatestDeployment(latest.version, latest.id, latest.requestedAt, latest.requestedBy, latest.enabledModules),
            instances,
            pending,
            isOrderPending(modules, latest.enabledModules),
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

    private DeploymentInfo.CoreInstance instance(CoreInstanceEntity entity)
    {
        return new DeploymentInfo.CoreInstance(
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
    private static boolean isPending(ModuleMetadata module, List<String> latest)
    {
        return !module.missing() && module.enabled() != latest.contains(module.type().name());
    }

    /* Whether the latest deployment holds its modules in another order than the registry; a toggle is pending apart */
    private static boolean isOrderPending(List<ModuleMetadata> modules, List<String> latest)
    {
        List<String> registered = modules.stream()
            .sorted(ModuleMetadata.ORDERING)
            .map(module -> module.type().name())
            .filter(latest::contains)
            .toList();
        List<String> deployed = latest.stream()
            .filter(registered::contains)
            .toList();
        return !registered.equals(deployed);
    }

    private static ModuleInfo info(ModuleMetadata module, Set<ModuleType> deployedTypes, List<String> latest, Map<String, Long> counts)
    {
        return new ModuleInfo(
            module.type(),
            module.kind(),
            module.assetClass(),
            module.enabled(),
            module.creatable(),
            deployedTypes.contains(module.type()),
            isPending(module, latest),
            module.missing(),
            module.source(),
            module.ordering(),
            module.provides(),
            counts.getOrDefault(module.type().name(), 0L)
        );
    }
}
