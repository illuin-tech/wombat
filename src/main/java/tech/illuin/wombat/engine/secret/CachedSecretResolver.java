package tech.illuin.wombat.engine.secret;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import tech.illuin.wombat.core.secret.MissingSecretException;
import tech.illuin.wombat.core.secret.SecretResolver;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Keeps resolved secrets in memory for a fixed time, so a {@link tech.illuin.wombat.core.secret.Secret} fetched on every
 * poll does not walk the whole resolver chain (database, Vault) each time.
 * <p>
 * Only hits are cached: a missing key is looked up again on each call, so a newly provisioned secret shows up at once.
 * Errors are not cached either. Entries expire after write, so a value rotated in a backend is seen within the TTL.
 */
public final class CachedSecretResolver implements SecretResolver
{
    private final SecretResolver delegate;
    private final Cache<String, String> cache;

    private static final long MAXIMUM_SIZE = 1_000;

    public CachedSecretResolver(SecretResolver delegate, Duration ttl)
    {
        this(delegate, ttl, Ticker.systemTicker());
    }

    CachedSecretResolver(SecretResolver delegate, Duration ttl, Ticker ticker)
    {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Objects.requireNonNull(ttl, "ttl cannot be null"))
            .maximumSize(MAXIMUM_SIZE)
            .ticker(ticker)
            .build();
    }

    public SecretResolver delegate()
    {
        return this.delegate;
    }

    @Override
    public String require(String key)
    {
        return this.find(key).orElseThrow(() -> new MissingSecretException(key));
    }

    @Override
    public Optional<String> find(String key)
    {
        if (key == null || key.isBlank())
            throw new IllegalArgumentException("key cannot be null or blank");

        /* A null from the loader stores nothing, which is how misses stay uncached */
        return Optional.ofNullable(this.cache.get(key, k -> this.delegate.find(k).orElse(null)));
    }

    /** Drops {@code key}, so the next lookup reads the backends again: called after a write through the API. */
    public void invalidate(String key)
    {
        this.cache.invalidate(key);
    }
}
