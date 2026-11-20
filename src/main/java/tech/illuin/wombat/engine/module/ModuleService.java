package tech.illuin.wombat.engine.module;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tech.illuin.wombat.engine.module.registry.UnknownModuleException;
import tech.illuin.wombat.engine.module.runtime.CoreRuntime;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The modules administration view, for {@code ModuleController} only: the rest of the app reads the modules from the
 * {@link ModuleRegistry}. Adds to the registry's state what only the running core knows, and the asset counts.
 */
@ApplicationScoped
public class ModuleService
{
    private final ModuleRegistry registry;
    private final AssetRepository assets;
    private final CoreRuntime runtime;

    /* Detected modules first, then missing ones, each by name */
    private static final Comparator<ModuleInfo> ORDER = Comparator.comparing(ModuleInfo::missing)
        .thenComparing(module -> module.type().name());

    public ModuleService(ModuleRegistry registry, AssetRepository assets, CoreRuntime runtime)
    {
        this.registry = registry;
        this.assets = assets;
        this.runtime = runtime;
    }

    @Transactional
    public List<ModuleInfo> list()
    {
        Map<String, Long> counts = this.assets.countVisibleByType();
        Set<AssetType> deployed = this.runtime.current().types();
        return this.registry.listModules().stream()
            .map(module -> info(module, deployed, counts))
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
            .map(module -> info(module, this.runtime.current().types(), this.assets.countVisibleByType()))
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

    /**
     * See {@link ModuleRegistry#remove}.
     */
    @Transactional
    public void remove(String name)
    {
        this.registry.remove(name);
    }

    private static ModuleInfo info(ModuleMetadata module, Set<AssetType> deployedTypes, Map<String, Long> counts)
    {
        boolean deployed = deployedTypes.contains(module.type());
        return new ModuleInfo(
            module.type(),
            module.assetClass(),
            module.enabled(),
            module.creatable(),
            deployed,
            /* A redeploy cannot bring a missing module in. Against the local core for now, against the latest deployment once redeploys are versioned */
            !module.missing() && module.enabled() != deployed,
            module.missing(),
            module.source(),
            counts.getOrDefault(module.type().name(), 0L)
        );
    }
}
