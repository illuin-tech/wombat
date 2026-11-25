package tech.illuin.wombat.engine.secret;

import tech.illuin.wombat.core.secret.SecretMetadata;
import tech.illuin.wombat.core.secret.SecretWriter;

import java.util.List;
import java.util.Objects;

/**
 * Invalidates a key in the {@link CachedSecretResolver} once it is written or deleted, so a change made through the API
 * applies at once on this instance instead of after the cache's TTL.
 */
public final class InvalidatingSecretWriter implements SecretWriter
{
    private final SecretWriter delegate;
    private final CachedSecretResolver cache;

    public InvalidatingSecretWriter(SecretWriter delegate, CachedSecretResolver cache)
    {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
    }

    public SecretWriter delegate()
    {
        return this.delegate;
    }

    @Override
    public List<SecretMetadata> listSecrets()
    {
        return this.delegate.listSecrets();
    }

    /* Invalidated even on failure: a write can land in the backend and still time out on our side */
    @Override
    public void putSecret(String key, String value)
    {
        try {
            this.delegate.putSecret(key, value);
        }
        finally {
            this.cache.invalidate(key);
        }
    }

    @Override
    public boolean deleteSecret(String key)
    {
        try {
            return this.delegate.deleteSecret(key);
        }
        finally {
            this.cache.invalidate(key);
        }
    }
}
