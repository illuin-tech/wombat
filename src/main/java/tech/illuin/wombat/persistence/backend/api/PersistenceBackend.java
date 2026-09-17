package tech.illuin.wombat.persistence.backend.api;

import java.util.Collection;
import java.util.Set;

import static java.util.Collections.emptyList;

public interface PersistenceBackend
{
    String id();

    boolean enabled();

    Set<HookSupplier> hooks();

    Set<ProcessSupplier> processes();

    static PersistenceBackend of(
        String id,
        Collection<HookSupplier> hooks,
        Collection<ProcessSupplier> processes
    ) {
        return new CompositeBackend(id, hooks, processes, true);
    }

    static PersistenceBackend disabled(String id)
    {
        return new CompositeBackend(id, emptyList(), emptyList(), false);
    }
}
