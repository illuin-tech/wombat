package tech.illuin.wombat.engine.module;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.core.asset.type.ModuleType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.module.persistence.CoreDeploymentEntity;
import tech.illuin.wombat.engine.module.persistence.CoreInstanceEntity;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata.Feature;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata.Kind;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tech.illuin.wombat.engine.module.registry.UnknownModuleException;
import tech.illuin.wombat.engine.module.runtime.CoreDeploymentService;
import tech.illuin.wombat.engine.module.runtime.CoreDeploymentService.Redeploy;
import tech.illuin.wombat.engine.module.runtime.CoreRuntime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        List<ModuleMetadata> modules = this.registry.listModules();
        Set<String> moved = moved(modules, latest);
        return modules.stream()
            .sorted(ModuleMetadata.ORDERING)
            .map(module -> info(module, deployed, latest, moved, counts))
            .toList();
    }

    /**
     * @throws UnknownModuleException if the module is not registered
     */
    @Transactional
    public ModuleInfo find(String name)
    {
        List<String> latest = this.latest().enabledModules;
        /* Whether it moved depends on the places of the others */
        Set<String> moved = moved(this.registry.listModules(), latest);
        return this.registry.getModule(name)
            .map(module -> info(module, this.runtime.current().types(), latest, moved, this.assets.countVisibleByType()))
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

    /**
     * See {@link ModulePrecedence}: the latest deployment against the next one, which holds the enabled modules in the
     * order of the modules.
     */
    @Transactional
    public ModulePrecedence precedence()
    {
        List<ModuleMetadata> modules = this.registry.listModules();
        Map<String, ModuleMetadata> byName = modules.stream()
            .collect(Collectors.toMap(module -> module.type().name(), Function.identity()));
        Map<ServiceFamily, Map<Feature, List<String>>> deployed = providers(this.latest().enabledModules.stream()
            .map(byName::get)
            .filter(Objects::nonNull)
            .toList());
        Map<ServiceFamily, Map<Feature, List<String>>> next = providers(modules.stream()
            .filter(ModuleMetadata::enabled)
            .sorted(ModuleMetadata.ORDERING)
            .toList());

        Set<ServiceFamily> families = EnumSet.noneOf(ServiceFamily.class);
        families.addAll(deployed.keySet());
        families.addAll(next.keySet());
        List<ModulePrecedence.FamilyPrecedence> precedence = families.stream()
            .map(family -> familyPrecedence(family, deployed.getOrDefault(family, Map.of()), next.getOrDefault(family, Map.of())))
            .toList();
        boolean changed = precedence.stream()
            .flatMap(family -> family.features().stream())
            .anyMatch(ModulePrecedence.FeaturePrecedence::changed);
        return new ModulePrecedence(precedence, changed);
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
            List.copyOf(moved(modules, latest.enabledModules)),
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

    /**
     * The modules the latest deployment holds at another place than the registry's order: the fewest which, moved, give
     * that order, i.e. those out of the longest sequence both orders share. In the registry's order; never a missing
     * module, nor one the latest deployment does not hold, which a toggle tells.
     */
    private static Set<String> moved(List<ModuleMetadata> modules, List<String> latest)
    {
        List<String> registered = modules.stream()
            .filter(module -> !module.missing())
            .sorted(ModuleMetadata.ORDERING)
            .map(module -> module.type().name())
            .filter(latest::contains)
            .toList();
        List<String> deployed = latest.stream()
            .filter(registered::contains)
            .toList();
        Set<String> kept = new HashSet<>(longestCommonSequence(registered, deployed));
        return registered.stream()
            .filter(name -> !kept.contains(name))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /* The longest sequence of names found in both lists in the same order, though not necessarily side by side */
    private static List<String> longestCommonSequence(List<String> first, List<String> second)
    {
        int[][] lengths = new int[first.size() + 1][second.size() + 1];
        for (int i = first.size() - 1; i >= 0; i--)
        {
            for (int j = second.size() - 1; j >= 0; j--)
                lengths[i][j] = first.get(i).equals(second.get(j)) ? lengths[i + 1][j + 1] + 1 : Math.max(lengths[i + 1][j], lengths[i][j + 1]);
        }

        List<String> common = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < first.size() && j < second.size())
        {
            if (first.get(i).equals(second.get(j)))
            {
                common.add(first.get(i));
                i++;
                j++;
            }
            else if (lengths[i + 1][j] >= lengths[i][j + 1])
                i++;
            else
                j++;
        }
        return common;
    }

    /* For each family and component, the family modules providing it, in the order they are given */
    private static Map<ServiceFamily, Map<Feature, List<String>>> providers(List<ModuleMetadata> modules)
    {
        Map<ServiceFamily, Map<Feature, List<String>>> providers = new EnumMap<>(ServiceFamily.class);
        for (ModuleMetadata module : modules)
        {
            if (module.kind() != Kind.family)
                continue;
            for (Feature feature : module.provides())
            {
                providers.computeIfAbsent(module.type().family(), family -> new EnumMap<>(Feature.class))
                    .computeIfAbsent(feature, provided -> new ArrayList<>())
                    .add(module.type().name());
            }
        }
        return providers;
    }

    private static ModulePrecedence.FamilyPrecedence familyPrecedence(
        ServiceFamily family,
        Map<Feature, List<String>> deployed,
        Map<Feature, List<String>> next
    ) {
        Set<Feature> features = EnumSet.noneOf(Feature.class);
        features.addAll(deployed.keySet());
        features.addAll(next.keySet());
        List<ModulePrecedence.FeaturePrecedence> precedence = features.stream()
            .map(feature -> {
                List<String> before = List.copyOf(deployed.getOrDefault(feature, List.of()));
                List<String> after = List.copyOf(next.getOrDefault(feature, List.of()));
                return new ModulePrecedence.FeaturePrecedence(feature, before, after, !before.equals(after));
            })
            .toList();
        return new ModulePrecedence.FamilyPrecedence(family, precedence);
    }

    private static ModuleInfo info(ModuleMetadata module, Set<ModuleType> deployedTypes, List<String> latest, Set<String> moved, Map<String, Long> counts)
    {
        return new ModuleInfo(
            module.type(),
            module.kind(),
            module.assetClass(),
            module.enabled(),
            module.creatable(),
            deployedTypes.contains(module.type()),
            isPending(module, latest) || moved.contains(module.type().name()),
            module.missing(),
            module.source(),
            module.ordering(),
            module.provides(),
            counts.getOrDefault(module.type().name(), 0L)
        );
    }
}
