package tech.illuin.wombat.engine.module.registry;

import tech.illuin.wombat.core.module.WombatModule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A registry of the modules registered on it, listed in registration order, none of them missing; its state is lost
 * with it.
 */
public class InMemoryModuleRegistry implements ModuleRegistry
{
    private final Map<String, ModuleMetadata> modules;

    public InMemoryModuleRegistry()
    {
        this.modules = new LinkedHashMap<>();
    }

    public synchronized void registerModule(WombatModule module, boolean enabled)
    {
        this.modules.put(module.type().name(), ModuleMetadata.core(module.type(), module.assetClass().orElse(null), enabled));
    }

    @Override
    public synchronized List<ModuleMetadata> listModules()
    {
        return List.copyOf(this.modules.values());
    }

    @Override
    public synchronized Optional<ModuleMetadata> getModule(String module)
    {
        return Optional.ofNullable(this.modules.get(module));
    }

    @Override
    public synchronized ModuleMetadata setEnabled(String module, boolean enabled, String user)
    {
        ModuleMetadata current = this.getModule(module)
            .orElseThrow(() -> new UnknownModuleException(module));
        ModuleMetadata updated = ModuleMetadata.core(current.type(), current.assetClass(), enabled);
        this.modules.put(module, updated);
        return updated;
    }

    /** No module is missing here, so none can be removed. */
    @Override
    public synchronized void remove(String module)
    {
        if (!this.modules.containsKey(module))
            throw new UnknownModuleException(module);
        throw new ModuleConflictException("Module " + module + " is detected, only a missing module can be removed");
    }
}
