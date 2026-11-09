package tech.illuin.wombat.engine.secret.database;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import tech.illuin.wombat.engine.secret.SecretConfig;
import tech.illuin.wombat.engine.secret.SecretProperties;
import tech.illuin.wombat.engine.secret.WritableBackend;
import tech.illuin.wombat.engine.secret.database.crypto.KeyEncryptionKeyProvider;

import static tech.illuin.wombat.engine.secret.SecretProperties.WriteMode.database;

@ApplicationScoped
public class DatabaseSecretConfig
{
    @Produces @Singleton @Priority(40) @WritableBackend
    public SecretConfig.SecretResolverCandidate provideDatabaseSecretBackend(
        SecretProperties properties,
        Instance<DatabaseSecretStore> store,
        Instance<KeyEncryptionKeyProvider> keyProviders
    ) {
        if (properties.writeMode() != database)
            return SecretConfig.SecretResolverCandidate.disabled();

        keyProviders.get().activeKeyId();
        return SecretConfig.SecretResolverCandidate.of(store.get());
    }
}
