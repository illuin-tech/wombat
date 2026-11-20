package tech.illuin.wombat.engine.module.runtime;

/**
 * A redeploy requested at the same time as another one, by another instance.
 */
public class DeploymentConflictException extends RuntimeException
{
    public DeploymentConflictException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
