package tech.illuin.wombat.engine.context.asset;

/**
 * An asset clashing with an existing one, or a provisioned asset edited through the API.
 */
public class AssetConflictException extends RuntimeException
{
    public AssetConflictException(String message)
    {
        super(message);
    }
}
