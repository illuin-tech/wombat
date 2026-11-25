package tech.illuin.wombat.engine.module.registry;

import tech.illuin.wombat.core.asset.type.ServiceFamily;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * An order of the family modules of a family which does not list each of them exactly once.
 */
public class ModuleOrderException extends RuntimeException
{
    public ModuleOrderException(String message)
    {
        super(message);
    }

    /**
     * @param members the family modules registered for {@code family}
     * @throws ModuleOrderException unless {@code order} lists each of {@code members} exactly once
     */
    static void check(ServiceFamily family, Collection<String> members, List<String> order)
    {
        if (order.size() != members.size() || !new HashSet<>(order).equals(new HashSet<>(members)))
            throw new ModuleOrderException("The order of the " + family + " family must list each of its family modules once: " + members);
    }
}
