package tech.illuin.wombat.engine.secret.database;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.secret.SecretConfig;
import tech.illuin.wombat.engine.secret.SecretProperties;
import tech.illuin.wombat.engine.secret.database.crypto.KeyEncryptionKeyProvider;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatabaseSecretConfigTest
{
    @Test
    @SuppressWarnings("unchecked")
    void disabledDescriptorDoesNotResolveTheStoreOrKekProvider()
    {
        Instance<DatabaseSecretStore> stores = mock(Instance.class);
        Instance<KeyEncryptionKeyProvider> providers = mock(Instance.class);

        SecretConfig.SecretResolverCandidate backend = new DatabaseSecretConfig().provideDatabaseSecretBackend(load(Map.of()), stores, providers);

        assertFalse(backend.isElected());
        verifyNoInteractions(stores, providers);
    }

    @Test
    @SuppressWarnings("unchecked")
    void validatesKekBeforeResolvingTheSelectedStore()
    {
        Instance<DatabaseSecretStore> stores = mock(Instance.class);
        Instance<KeyEncryptionKeyProvider> providers = mock(Instance.class);
        KeyEncryptionKeyProvider provider = mock(KeyEncryptionKeyProvider.class);
        when(providers.get()).thenReturn(provider);
        when(provider.activeKeyId()).thenThrow(new IllegalArgumentException("Invalid KEK configuration"));

        assertThrows(IllegalArgumentException.class, () -> new DatabaseSecretConfig().provideDatabaseSecretBackend(
            load(Map.of("wombat.secret.write-mode", "DATABASE")), stores, providers
        ));
        verifyNoInteractions(stores);
    }

    @Test
    @SuppressWarnings("unchecked")
    void enabledDescriptorContainsTheSelectedStore()
    {
        Instance<DatabaseSecretStore> stores = mock(Instance.class);
        Instance<KeyEncryptionKeyProvider> providers = mock(Instance.class);
        DatabaseSecretStore store = mock(DatabaseSecretStore.class);
        KeyEncryptionKeyProvider provider = mock(KeyEncryptionKeyProvider.class);
        when(stores.get()).thenReturn(store);
        when(providers.get()).thenReturn(provider);

        SecretConfig.SecretResolverCandidate backend = new DatabaseSecretConfig().provideDatabaseSecretBackend(
            load(Map.of("wombat.secret.write-mode", "DATABASE")), stores, providers
        );

        assertTrue(backend.isElected());
        assertSame(store, backend.resolver());
        verify(provider).activeKeyId();
        verify(stores).get();
    }

    private static SecretProperties load(Map<String, String> values)
    {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
            .withMapping(SecretProperties.class)
            .withSources(new PropertiesConfigSource(values, "test", 100))
            .build();
        return config.getConfigMapping(SecretProperties.class);
    }
}