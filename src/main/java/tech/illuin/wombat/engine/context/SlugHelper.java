package tech.illuin.wombat.engine.context;

import java.util.Locale;

/**
 * The ids of the environments and assets created through the API. Those declared by the monitored-environments file
 * or a worker are not held to it: worker assets are qualified as {@code workerId.assetId}, so they can never clash with
 * one.
 */
public final class SlugHelper
{
    public static final String PATTERN = "^[a-z0-9_-]+$";
    public static final String MESSAGE = "must only contain lowercase letters, digits, dashes and underscores";
    public static final int MAX_LENGTH = 64;

    private SlugHelper() {}

    /**
     * A valid id derived from any other, e.g. {@code worker-1-chatbot} from the worker asset {@code worker-1.chatbot}.
     */
    public static String slugify(String value)
    {
        String slug = value.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_-]+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH)
            slug = slug.substring(0, MAX_LENGTH);
        return slug.isEmpty() ? "asset" : slug;
    }
}
