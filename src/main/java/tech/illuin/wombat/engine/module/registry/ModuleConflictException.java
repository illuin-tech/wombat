package tech.illuin.wombat.engine.module.registry;

/**
 * A missing module toggled, or a detected module deleted.
 */
public class ModuleConflictException extends RuntimeException
{
    public ModuleConflictException(String message)
    {
        super(message);
    }
}
