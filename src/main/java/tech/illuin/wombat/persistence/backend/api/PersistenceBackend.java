package tech.illuin.wombat.persistence.backend.api;

import tech.illuin.wombat.persistence.store.MetricStores;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptyList;

public interface PersistenceBackend
{
    String id();

    boolean enabled();

    Set<HookSupplier> hooks();

    Set<ProcessSupplier> processes();

    Optional<MetricStores> stores();

    static PersistenceBackend of(
        String id,
        Collection<HookSupplier> hooks,
        Collection<ProcessSupplier> processes,
        MetricStores stores
    ) {
        return new CompositeBackend(id, hooks, processes, stores, true);
    }

    static PersistenceBackend disabled(String id)
    {
        return new CompositeBackend(id, emptyList(), emptyList(), null, false);
    }
}
