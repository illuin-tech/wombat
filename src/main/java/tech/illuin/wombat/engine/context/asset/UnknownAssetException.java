package tech.illuin.wombat.engine.context.asset;

/**
 * An asset that does not exist, or was soft-deleted.
 */
public class UnknownAssetException extends RuntimeException
{
    public UnknownAssetException(String assetId)
    {
        super("Unknown asset: " + assetId);
    }
}
