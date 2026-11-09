package tech.illuin.wombat.engine.secret.database;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.core.secret.MissingSecretException;
import tech.illuin.wombat.core.secret.SecretMetadata;
import tech.illuin.wombat.engine.secret.SecretStore;
import tech.illuin.wombat.engine.secret.database.crypto.SecretCryptography;
import tech.illuin.wombat.engine.secret.database.persistence.SecretEntity;
import tech.illuin.wombat.engine.secret.database.persistence.SecretRepository;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Typed({DatabaseSecretStore.class, SecretStore.class})
public class DatabaseSecretStore implements SecretStore
{
    private final SecretRepository secrets;
    private final SecretCryptography cryptography;

    public DatabaseSecretStore(SecretRepository secrets, SecretCryptography cryptography)
    {
        this.secrets = secrets;
        this.cryptography = cryptography;
    }

    @Override @Transactional
    public Optional<String> find(String key)
    {
        validateKey(key);
        SecretEntity secret = this.secrets.findById(key);
        return secret == null ? Optional.empty() : Optional.of(this.cryptography.decrypt(encrypted(secret)));
    }

    @Override @Transactional
    public String require(String key)
    {
        return find(key).orElseThrow(() -> new MissingSecretException(key));
    }

    @Override @Transactional
    public void putSecret(String key, String value)
    {
        validateKey(key);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Secret value must not be null or blank");

        SecretCryptography.EncryptedSecret encrypted = this.cryptography.encrypt(key, value);
        SecretEntity secret = this.secrets.findById(key);
        if (secret == null)
        {
            secret = new SecretEntity();
            secret.key = key;
        }
        apply(encrypted, secret);
        if (secret.createdAt == null)
            this.secrets.persist(secret);
    }

    @Override @Transactional
    public boolean deleteSecret(String key)
    {
        validateKey(key);
        return this.secrets.deleteById(key);
    }

    @Override @Transactional
    public List<SecretMetadata> listSecrets()
    {
        return this.secrets.listMetadata();
    }

    private static SecretCryptography.EncryptedSecret encrypted(SecretEntity secret)
    {
        return new SecretCryptography.EncryptedSecret(
            secret.key,
            secret.kekId,
            secret.ciphertext,
            secret.payloadNonce,
            secret.wrappedDek,
            secret.wrappingNonce
        );
    }

    private static void apply(SecretCryptography.EncryptedSecret encrypted, SecretEntity secret)
    {
        secret.ciphertext = encrypted.ciphertext();
        secret.payloadNonce = encrypted.payloadNonce();
        secret.wrappedDek = encrypted.wrappedDek();
        secret.wrappingNonce = encrypted.wrappingNonce();
        secret.kekId = encrypted.kekId();
    }

    private static void validateKey(String key)
    {
        if (key == null || key.isBlank())
            throw new IllegalArgumentException("Secret key must not be null or blank");
    }
}
