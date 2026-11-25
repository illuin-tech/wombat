package tech.illuin.wombat.engine.module.registry;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * An order of the modules which does not list each registered module exactly once.
 */
public class ModuleOrderException extends RuntimeException
{
    public ModuleOrderException(String message)
    {
        super(message);
    }

    /**
     * @param registered the registered modules
     * @throws ModuleOrderException unless {@code order} lists each of {@code registered} exactly once
     */
    static void check(Collection<String> registered, List<String> order)
    {
        if (order.size() != registered.size() || !new HashSet<>(order).equals(new HashSet<>(registered)))
            throw new ModuleOrderException("The order must list each registered module once: " + registered);
    }
}
