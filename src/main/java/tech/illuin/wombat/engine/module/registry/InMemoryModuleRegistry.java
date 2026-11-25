package tech.illuin.wombat.engine.module.registry;

import tech.illuin.wombat.core.asset.type.FamilyType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.module.WombatModule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A registry of the modules registered on it, listed in registration order, none of them missing; its state is lost
 * with it. A family module is placed last in its family when registered.
 */
public class InMemoryModuleRegistry implements ModuleRegistry
{
    private final Map<String, Registration> modules;

    public InMemoryModuleRegistry()
    {
        this.modules = new LinkedHashMap<>();
    }

    /**
     * Registers {@code module}, or sets the state of a registered one, which keeps its ordering.
     */
    public synchronized void registerModule(WombatModule module, boolean enabled)
    {
        Registration registered = this.modules.get(module.type().name());
        Integer ordering = registered != null ? registered.ordering() : this.nextOrdering(module);
        this.modules.put(module.type().name(), new Registration(module, enabled, ordering));
    }

    @Override
    public synchronized List<ModuleMetadata> listModules()
    {
        return this.modules.values().stream()
            .map(Registration::metadata)
            .toList();
    }

    @Override
    public synchronized Optional<ModuleMetadata> getModule(String module)
    {
        return Optional.ofNullable(this.modules.get(module))
            .map(Registration::metadata);
    }

    @Override
    public synchronized ModuleMetadata setEnabled(String module, boolean enabled, String user)
    {
        Registration registered = Optional.ofNullable(this.modules.get(module))
            .orElseThrow(() -> new UnknownModuleException(module));
        Registration updated = new Registration(registered.module(), enabled, registered.ordering());
        this.modules.put(module, updated);
        return updated.metadata();
    }

    @Override
    public synchronized void setFamilyOrder(ServiceFamily family, List<String> modules, String user)
    {
        List<String> members = this.modules.values().stream()
            .filter(registered -> registered.module().type() instanceof FamilyType type && type.family() == family)
            .map(registered -> registered.module().type().name())
            .toList();
        ModuleOrderException.check(family, members, modules);

        for (int index = 0; index < modules.size(); index++)
        {
            Registration registered = this.modules.get(modules.get(index));
            this.modules.put(modules.get(index), new Registration(registered.module(), registered.enabled(), index + 1));
        }
    }

    /** No module is missing here, so none can be removed. */
    @Override
    public synchronized void remove(String module)
    {
        if (!this.modules.containsKey(module))
            throw new UnknownModuleException(module);
        throw new ModuleConflictException("Module " + module + " is detected, only a missing module can be removed");
    }

    /* Null for an asset-type module, which has no ordering */
    private Integer nextOrdering(WombatModule module)
    {
        if (!(module.type() instanceof FamilyType type))
            return null;
        return this.modules.values().stream()
            .filter(registered -> registered.module().type() instanceof FamilyType other && other.family() == type.family())
            .mapToInt(Registration::ordering)
            .max()
            .orElse(0) + 1;
    }

    private record Registration(WombatModule module, boolean enabled, Integer ordering)
    {
        ModuleMetadata metadata()
        {
            return ModuleMetadata.core(this.module, this.enabled, this.ordering);
        }
    }
}
