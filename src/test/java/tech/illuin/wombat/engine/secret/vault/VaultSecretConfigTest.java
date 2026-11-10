package tech.illuin.wombat.engine.secret.vault;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.secret.SecretConfig;
import tech.illuin.wombat.engine.secret.SecretProperties;

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
            properties(WriteMode.vault.name()), stores
        );

        assertTrue(candidate.isElected());
        assertSame(store, candidate.resolver());
        verify(stores).get();
    }

    private static SecretProperties properties(String writeMode)
    {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
            .withMapping(SecretProperties.class)
            .withSources(new PropertiesConfigSource(Map.of("wombat.secret.write-mode", writeMode), "test", 100))
            .build();
        return config.getConfigMapping(SecretProperties.class);
    }
}
