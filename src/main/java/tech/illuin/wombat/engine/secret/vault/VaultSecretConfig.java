package tech.illuin.wombat.engine.secret.vault;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import tech.illuin.wombat.engine.secret.SecretConfig;
import tech.illuin.wombat.engine.secret.SecretProperties;
import tech.illuin.wombat.engine.secret.WritableBackend;

import static tech.illuin.wombat.engine.secret.SecretProperties.WriteMode.vault;

@ApplicationScoped
public class VaultSecretConfig
{
    @Produces @Singleton @Priority(40) @WritableBackend
    public SecretConfig.SecretResolverCandidate provideVaultSecretBackend(
        SecretProperties properties,
        Instance<VaultSecretStore> store
    ) {
        if (properties.writeMode() != vault)
            return SecretConfig.SecretResolverCandidate.disabled();

        return SecretConfig.SecretResolverCandidate.of(store.get());
    }
}
