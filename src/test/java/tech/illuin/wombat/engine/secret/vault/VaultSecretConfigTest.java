package tech.illuin.wombat.engine.secret.vault;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.secret.SecretConfig;
import tech.illuin.wombat.engine.secret.SecretProperties;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.illuin.wombat.engine.secret.SecretProperties.WriteMode;

class VaultSecretConfigTest
{
    @Test
    @SuppressWarnings("unchecked")
    void nonVaultModesDoNotResolveVaultStore()
    {
        Instance<VaultSecretStore> stores = mock(Instance.class);
        VaultSecretConfig config = new VaultSecretConfig();

        SecretConfig.SecretResolverCandidate disabled = config.provideVaultSecretBackend(properties(WriteMode.disabled.name()), stores);
        SecretConfig.SecretResolverCandidate database = config.provideVaultSecretBackend(properties(WriteMode.database.name()), stores);

        assertFalse(disabled.isElected());
        assertFalse(database.isElected());
        verifyNoInteractions(stores);
    }

    @Test
    @SuppressWarnings("unchecked")
    void vaultModeElectsTheLazyStore()
    {
        Instance<VaultSecretStore> stores = mock(Instance.class);
        VaultSecretStore store = mock(VaultSecretStore.class);
        when(stores.get()).thenReturn(store);

        SecretConfig.SecretResolverCandidate candidate = new VaultSecretConfig().provideVaultSecretBackend(
            properties(WriteMode.vault.name(), true), stores
        );

        assertTrue(candidate.isElected());
        assertSame(store, candidate.resolver());
        verify(stores).get();
    }

    @Test
    @SuppressWarnings("unchecked")
    void vaultOffElectsNothing()
    {
        Instance<VaultSecretStore> stores = mock(Instance.class);
        Instance<VaultSecretResolver> resolvers = mock(Instance.class);
        VaultSecretConfig config = new VaultSecretConfig();

        for (WriteMode mode : List.of(WriteMode.disabled, WriteMode.database))
        {
            assertFalse(config.provideVaultSecretBackend(properties(mode.name()), stores).isElected());
            assertFalse(config.provideVaultSecretResolver(properties(mode.name()), resolvers).isElected());
        }
        verifyNoInteractions(stores, resolvers);
    }

    @Test
    @SuppressWarnings("unchecked")
    void enabledVaultIsReadOnlyUnlessItIsTheWriteMode()
    {
        Instance<VaultSecretStore> stores = mock(Instance.class);
        Instance<VaultSecretResolver> resolvers = mock(Instance.class);
        VaultSecretResolver resolver = mock(VaultSecretResolver.class);
        when(resolvers.get()).thenReturn(resolver);
        VaultSecretConfig config = new VaultSecretConfig();

        for (WriteMode mode : List.of(WriteMode.disabled, WriteMode.database))
        {
            SecretProperties properties = properties(mode.name(), true);
            SecretConfig.SecretResolverCandidate readOnly = config.provideVaultSecretResolver(properties, resolvers);

            assertTrue(readOnly.isElected());
            assertSame(resolver, readOnly.resolver());
            assertFalse(config.provideVaultSecretBackend(properties, stores).isElected());
        }
        verifyNoInteractions(stores);
    }

    @Test
    @SuppressWarnings("unchecked")
    void vaultWriteModeElectsOnlyTheStore()
    {
        Instance<VaultSecretStore> stores = mock(Instance.class);
        Instance<VaultSecretResolver> resolvers = mock(Instance.class);
        when(stores.get()).thenReturn(mock(VaultSecretStore.class));
        VaultSecretConfig config = new VaultSecretConfig();
        SecretProperties properties = properties(WriteMode.vault.name(), true);

        assertTrue(config.provideVaultSecretBackend(properties, stores).isElected());
        assertFalse(config.provideVaultSecretResolver(properties, resolvers).isElected());
        verifyNoInteractions(resolvers);
    }

    @Test
    @SuppressWarnings("unchecked")
    void vaultWriteModeElectsNothingUnlessEnabled()
    {
        Instance<VaultSecretStore> stores = mock(Instance.class);
        Instance<VaultSecretResolver> resolvers = mock(Instance.class);
        VaultSecretConfig config = new VaultSecretConfig();
        SecretProperties properties = properties(WriteMode.vault.name(), false);

        assertFalse(config.provideVaultSecretBackend(properties, stores).isElected());
        assertFalse(config.provideVaultSecretResolver(properties, resolvers).isElected());
        verifyNoInteractions(stores, resolvers);
    }

    private static SecretProperties properties(String writeMode)
    {
        return properties(writeMode, false);
    }

    private static SecretProperties properties(String writeMode, boolean enabled)
    {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
            .withMapping(SecretProperties.class)
            .withSources(new PropertiesConfigSource(Map.of(
                "wombat.secret.write-mode", writeMode,
                "wombat.secret.vault.enabled", String.valueOf(enabled)
            ), "test", 100))
            .build();
        return config.getConfigMapping(SecretProperties.class);
    }
}
