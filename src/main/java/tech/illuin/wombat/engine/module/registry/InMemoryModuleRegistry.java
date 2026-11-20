package tech.illuin.wombat.engine.module.registry;

import tech.illuin.wombat.core.module.WombatModule;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A registry of the modules registered on it, none of them missing; its state is lost with it.
 */
public class InMemoryModuleRegistry implements ModuleRegistry
{
    private final Map<String, ModuleMetadata> modules;

    public InMemoryModuleRegistry()
    {
        this.modules = new ConcurrentHashMap<>();
    }

    public void registerModule(WombatModule module, boolean enabled)
    {
        this.modules.put(module.type().name(), ModuleMetadata.core(module.type(), module.assetClass(), enabled));
    }

    @Override
    public List<ModuleMetadata> listModules()
    {
        return this.modules.values().stream().toList();
    }

    @Override
    public Optional<ModuleMetadata> getModule(String module)
    {
        return Optional.ofNullable(this.modules.get(module));
    }

    @Override
    public ModuleMetadata setEnabled(String module, boolean enabled, String user)
    {
        ModuleMetadata current = this.getModule(module)
            .orElseThrow(() -> new UnknownModuleException(module));
        ModuleMetadata updated = ModuleMetadata.core(current.type(), current.assetClass(), enabled);
        this.modules.put(module, updated);
        return updated;
    }

    /** No module is missing here, so none can be removed. */
    @Override
    public void remove(String module)
    {
        if (!this.modules.containsKey(module))
            throw new UnknownModuleException(module);
        throw new ModuleConflictException("Module " + module + " is detected, only a missing module can be removed");
    }
}
