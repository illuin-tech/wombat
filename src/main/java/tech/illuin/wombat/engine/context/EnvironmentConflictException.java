package tech.illuin.wombat.engine.context;

/**
 * A declaration claims an asset which another declaration owns, see {@link EnvironmentReconciler}.
 */
public class EnvironmentConflictException extends RuntimeException
{
    public EnvironmentConflictException(String message)
    {
        super(message);
    }
}
