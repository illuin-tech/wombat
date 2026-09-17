package tech.illuin.wombat.persistence.backend.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;

public class CompositeBackend implements PersistenceBackend, AutoCloseable
{
    private final String id;
    private final Set<HookSupplier> hooks;
    private final Set<ProcessSupplier> processes;
    private final boolean enabled;

    private static final Logger logger = LoggerFactory.getLogger(CompositeBackend.class);

    public CompositeBackend(
        String id,
        Collection<HookSupplier> hooks,
        Collection<ProcessSupplier> processes,
        boolean enabled
    ) {
        this.id = id;
        this.hooks = Set.copyOf(hooks);
        this.processes = Set.copyOf(processes);
        this.enabled = enabled;
    }

    @Override
    public String id()
    {
        return this.id;
    }

    @Override
    public boolean enabled()
    {
        return this.enabled;
    }

    @Override
    public Set<HookSupplier> hooks()
    {
        return this.hooks;
    }

    @Override
    public Set<ProcessSupplier> processes()
    {
        return this.processes;
    }

    @Override
    public void close() throws Exception
    {
        logger.debug("Closing composite backend hooks ({} found)", this.hooks.size());
        for (HookSupplier hook : this.hooks)
        {
            if (hook.isSupplied() && hook.supply() instanceof AutoCloseable closeableHook)
                closeableHook.close();
        }

        logger.debug("Closing composite backend processes ({} found)", this.processes.size());
        for (ProcessSupplier process : this.processes)
        {
            if (process.isSupplied() && process.supply() instanceof AutoCloseable closeableProcess)
                closeableProcess.close();
        }
    }
}
