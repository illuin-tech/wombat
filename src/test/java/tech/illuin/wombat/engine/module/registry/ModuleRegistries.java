package tech.illuin.wombat.engine.module.registry;

import tech.illuin.wombat.core.module.WombatModule;

import java.util.List;

public final class ModuleRegistries
{
    private ModuleRegistries() {}

    public static ModuleRegistry enabled(List<? extends WombatModule> modules)
    {
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();
        modules.forEach(module -> registry.registerModule(module, true));
        return registry;
    }
}
