package tech.illuin.wombat.engine.module.registry;

/**
 * A module neither detected nor registered.
 */
public class UnknownModuleException extends RuntimeException
{
    public UnknownModuleException(String name)
    {
        super("No module could be found for id " + name);
    }
}
