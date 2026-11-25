package tech.illuin.wombat.engine.secret;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.secret.SecretWriter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvalidatingSecretWriterTest
{
    @Test
    void putInvalidatesTheKey()
    {
        SecretWriter delegate = mock(SecretWriter.class);
        CachedSecretResolver cache = mock(CachedSecretResolver.class);

        new InvalidatingSecretWriter(delegate, cache).putSecret("token", "value");

        verify(delegate).putSecret("token", "value");
        verify(cache).invalidate("token");
    }

    @Test
    void deleteInvalidatesTheKeyWhateverItReturns()
    {
        SecretWriter delegate = mock(SecretWriter.class);
        CachedSecretResolver cache = mock(CachedSecretResolver.class);
        when(delegate.deleteSecret("present")).thenReturn(true);
        InvalidatingSecretWriter writer = new InvalidatingSecretWriter(delegate, cache);

        assertTrue(writer.deleteSecret("present"));
        assertFalse(writer.deleteSecret("absent"));
        verify(cache).invalidate("present");
        verify(cache).invalidate("absent");
    }

    @Test
    void failedWriteStillInvalidatesTheKey()
    {
        SecretWriter delegate = mock(SecretWriter.class);
        CachedSecretResolver cache = mock(CachedSecretResolver.class);
        doThrow(new IllegalStateException("timed out")).when(delegate).putSecret("token", "value");
        InvalidatingSecretWriter writer = new InvalidatingSecretWriter(delegate, cache);

        assertThrows(IllegalStateException.class, () -> writer.putSecret("token", "value"));
        verify(cache).invalidate("token");
    }

    @Test
    void listingPassesThroughWithoutInvalidating()
    {
        SecretWriter delegate = mock(SecretWriter.class);
        CachedSecretResolver cache = mock(CachedSecretResolver.class);
        when(delegate.listSecrets()).thenReturn(List.of());

        assertEquals(List.of(), new InvalidatingSecretWriter(delegate, cache).listSecrets());
        verify(cache, never()).invalidate(any());
    }
}
