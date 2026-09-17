package tech.illuin.wombat.module.api;

import tech.illuin.wombat.core.module.WombatModule;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;

/**
 * A configured contribution of SDK modules, mirroring {@link tech.illuin.wombat.persistence.backend.api.PersistenceBackend}.
 * <p>
 * Each registration is produced unconditionally as a bean so the set of contributions is fixed at wiring time; what
 * configuration decides is whether a registration is {@link #enabled()}. A disabled registration carries no modules,
 * which keeps "this deployment has no Prometheus" a configuration outcome rather than a missing-bean failure.
 */
public interface ModuleRegistration
{
    String id();

    boolean enabled();

    Set<WombatModule> modules();

    static ModuleRegistration of(String id, WombatModule module)
    {
        return new CompositeModuleRegistration(id, List.of(module), true);
    }

    static ModuleRegistration of(String id, Collection<WombatModule> modules)
    {
        return new CompositeModuleRegistration(id, modules, true);
    }

    static ModuleRegistration disabled(String id)
    {
        return new CompositeModuleRegistration(id, emptyList(), false);
    }
}
