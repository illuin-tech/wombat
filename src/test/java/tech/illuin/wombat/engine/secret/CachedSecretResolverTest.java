package tech.illuin.wombat.engine.secret;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.secret.MissingSecretException;
import tech.illuin.wombat.core.secret.SecretResolver;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CachedSecretResolverTest
{
    private static final Duration TTL = Duration.ofMinutes(10);

    @Test
    void hitIsReadOnceWithinTheTtlAndAgainAfterIt()
    {
        SecretResolver delegate = mock(SecretResolver.class);
        when(delegate.find("token")).thenReturn(Optional.of("first"), Optional.of("rotated"));
        AtomicLong nanos = new AtomicLong();
        CachedSecretResolver resolver = new CachedSecretResolver(delegate, TTL, nanos::get);

        assertEquals("first", resolver.require("token"));
        nanos.addAndGet(TTL.minusSeconds(1).toNanos());
        assertEquals("first", resolver.find("token").orElseThrow());
        verify(delegate, times(1)).find("token");

        nanos.addAndGet(Duration.ofSeconds(1).toNanos());
        assertEquals("rotated", resolver.require("token"));
        verify(delegate, times(2)).find("token");
    }

    @Test
    void missIsNotCached()
    {
        SecretResolver delegate = mock(SecretResolver.class);
        when(delegate.find("token")).thenReturn(Optional.empty(), Optional.of("provisioned"));
        CachedSecretResolver resolver = new CachedSecretResolver(delegate, TTL, () -> 0L);

        assertTrue(resolver.find("token").isEmpty());
        assertEquals("provisioned", resolver.require("token"));
    }

    @Test
    void requireThrowsOnAMiss()
    {
        SecretResolver delegate = mock(SecretResolver.class);
        when(delegate.find("token")).thenReturn(Optional.empty());
        CachedSecretResolver resolver = new CachedSecretResolver(delegate, TTL, () -> 0L);

        assertThrows(MissingSecretException.class, () -> resolver.require("token"));
    }

    @Test
    void delegateFailureIsNotCached()
    {
        SecretResolver delegate = mock(SecretResolver.class);
        when(delegate.find("token"))
            .thenThrow(new IllegalStateException("Vault unreachable"))
            .thenReturn(Optional.of("recovered"));
        CachedSecretResolver resolver = new CachedSecretResolver(delegate, TTL, () -> 0L);

        assertThrows(IllegalStateException.class, () -> resolver.find("token"));
        assertEquals("recovered", resolver.require("token"));
    }

    @Test
    void blankKeyIsRejectedWithoutReadingTheDelegate()
    {
        SecretResolver delegate = mock(SecretResolver.class);
        CachedSecretResolver resolver = new CachedSecretResolver(delegate, TTL, () -> 0L);

        assertThrows(IllegalArgumentException.class, () -> resolver.find(null));
        assertThrows(IllegalArgumentException.class, () -> resolver.find(" "));
        verify(delegate, never()).find(any());
    }

    @Test
    void invalidateForcesTheNextRead()
    {
        SecretResolver delegate = mock(SecretResolver.class);
        when(delegate.find("token")).thenReturn(Optional.of("first"), Optional.of("written"));
        CachedSecretResolver resolver = new CachedSecretResolver(delegate, TTL, () -> 0L);

        assertEquals("first", resolver.require("token"));
        resolver.invalidate("token");
        assertEquals("written", resolver.require("token"));
    }
}
