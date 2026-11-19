package tech.illuin.wombat.engine.context;

/**
 * The ids of the environments and assets created through the API. Those declared by the monitored-environments file
 * or a worker are not held to it: worker assets are qualified as {@code workerId.assetId}, so they can never clash with
 * one.
 */
public final class SlugHelper
{
    public static final String PATTERN = "^[a-z0-9_-]+$";
    public static final String MESSAGE = "must only contain lowercase letters, digits, dashes and underscores";

    private SlugHelper() {}
}
