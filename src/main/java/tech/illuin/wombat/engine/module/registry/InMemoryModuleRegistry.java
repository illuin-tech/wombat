package tech.illuin.wombat.engine.module.registry;

import tech.illuin.wombat.core.module.WombatModule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryModuleRegistry implements ModuleRegistry
{
    private final Map<String, ModuleMetadata> modules;

    public InMemoryModuleRegistry()
    {
        this.modules = new HashMap<>();
    }

    public void registerModule(WombatModule module, boolean enabled)
    {
        this.modules.put(module.type().name(), new ModuleMetadata(
            module.type(),
            module.assetClass(),
            enabled
        ));
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
}
