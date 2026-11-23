package tech.illuin.wombat.engine.secret.vault;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.engine.secret.SecretConfig;
import tech.illuin.wombat.engine.secret.SecretProperties;
import tech.illuin.wombat.engine.secret.WritableBackend;

import static tech.illuin.wombat.engine.secret.SecretProperties.WriteMode.vault;

/**
 * Elects Vault, once enabled, in one of two roles: read-write when it is the write mode, read-only otherwise.
 * <p>
 * The role follows from {@code write-mode}, which already names the single writer, so no combination can conflict.
 */
@ApplicationScoped
public class VaultSecretConfig
{
    private static final Logger logger = LoggerFactory.getLogger(VaultSecretConfig.class);

    @Produces @Singleton @Priority(40) @WritableBackend
    public SecretConfig.SecretResolverCandidate provideVaultSecretBackend(
        SecretProperties properties,
        Instance<VaultSecretStore> store
    ) {
        if (!properties.vault().enabled() || properties.writeMode() != vault)
            return SecretConfig.SecretResolverCandidate.disabled();

        logger.info("Vault secret resolver enabled (read-write)");
        return SecretConfig.SecretResolverCandidate.of(store.get());
    }

    /* Below the writer, so a value set through the API wins, and above the local sources */
    @Produces @Singleton @Priority(35)
    public SecretConfig.SecretResolverCandidate provideVaultSecretResolver(
        SecretProperties properties,
        Instance<VaultSecretResolver> resolver
    ) {
        if (!properties.vault().enabled() || properties.writeMode() == vault)
            return SecretConfig.SecretResolverCandidate.disabled();

        logger.info("Vault secret resolver enabled (read-only)");
        return SecretConfig.SecretResolverCandidate.of(resolver.get());
    }
}
