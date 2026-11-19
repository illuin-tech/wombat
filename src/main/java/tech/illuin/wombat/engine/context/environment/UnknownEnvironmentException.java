package tech.illuin.wombat.engine.context.environment;

/**
 * An environment that does not exist, or was soft-deleted.
 */
public class UnknownEnvironmentException extends RuntimeException
{
    public UnknownEnvironmentException(String environmentId)
    {
        super("Unknown environment: " + environmentId);
    }
}
