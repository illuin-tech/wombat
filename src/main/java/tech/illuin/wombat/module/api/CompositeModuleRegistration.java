package tech.illuin.wombat.module.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.module.WombatModule;

import java.util.Collection;
import java.util.Set;

public class CompositeModuleRegistration implements ModuleRegistration, AutoCloseable
{
    private final String id;
    private final Set<WombatModule> modules;
    private final boolean enabled;

    private static final Logger logger = LoggerFactory.getLogger(CompositeModuleRegistration.class);

    public CompositeModuleRegistration(
        String id,
        Collection<WombatModule> modules,
        boolean enabled
    ) {
        this.id = id;
        this.modules = Set.copyOf(modules);
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
    public Set<WombatModule> modules()
    {
        return this.modules;
    }

    @Override
    public void close()
    {
        logger.debug("Closing module registration {} ({} modules found)", this.id, this.modules.size());
        for (WombatModule module : this.modules)
            module.close();
    }
}
