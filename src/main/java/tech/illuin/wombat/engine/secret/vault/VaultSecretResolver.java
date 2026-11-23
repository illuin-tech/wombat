package tech.illuin.wombat.engine.secret.vault;

import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretData;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretMetadataResultData;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretMetadataVersion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import tech.illuin.wombat.core.secret.MissingSecretException;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.engine.secret.SecretProperties;
import tech.illuin.wombat.engine.secret.vault.client.VaultKvV2Client;
import tech.illuin.wombat.engine.secret.vault.client.VaultPath;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Reads Wombat keys from Vault KV v2, on its own in read-only mode, or for {@link VaultSecretStore} in read-write mode.
 */
@ApplicationScoped
@Typed(VaultSecretResolver.class)
public class VaultSecretResolver implements SecretResolver
{
    private final VaultKvV2Client vault;
    private final String pathPrefix;

    public VaultSecretResolver(VaultKvV2Client vault, SecretProperties properties)
    {
        this.vault = vault;
        this.pathPrefix = VaultPath.validatePrefix(properties.vault().pathPrefix());
    }

    @Override
    public Optional<String> find(String key)
    {
        String path = VaultPath.keyPath(this.pathPrefix, key);
        Optional<ActiveVersion> activeVersion = this.activeVersion(path);
        if (activeVersion.isEmpty())
            return Optional.empty();

        VaultSecretsKV2ReadSecretData secret = this.vault.readSecret(path, activeVersion.orElseThrow().number());
        Map<String, Object> values = secret == null ? null : secret.getData();
        Object value = values == null ? null : values.get("value");
        if (!(value instanceof String stringValue) || stringValue.isBlank())
            throw new IllegalStateException("Vault secret is missing a non-blank string 'value' field");

        return Optional.of(stringValue);
    }

    @Override
    public String require(String key)
    {
        return this.find(key).orElseThrow(() -> new MissingSecretException(key));
    }

    String pathPrefix()
    {
        return this.pathPrefix;
    }

    Optional<ActiveVersion> activeVersion(String path)
    {
        return this.vault.readSecretMetadata(path)
            .flatMap(VaultSecretResolver::activeCurrentVersion);
    }

    static Optional<ActiveVersion> activeCurrentVersion(VaultSecretsKV2ReadSecretMetadataResultData metadata)
    {
        OptionalInt versionNumber = currentVersion(metadata);
        if (versionNumber.isEmpty())
            return Optional.empty();

        int version = versionNumber.getAsInt();
        VaultSecretsKV2ReadSecretMetadataVersion versionMetadata = versionMetadata(metadata, version);
        return isDeleted(versionMetadata) ? Optional.empty() : Optional.of(new ActiveVersion(version, versionMetadata));
    }

    static IllegalStateException malformedMetadata()
    {
        return new IllegalStateException("Vault returned malformed metadata for a secret path");
    }

    private static OptionalInt currentVersion(VaultSecretsKV2ReadSecretMetadataResultData metadata)
    {
        Integer version = metadata.getCurrentVersion();
        if (version == null || version < 0)
            throw malformedMetadata();
        return version == 0 ? OptionalInt.empty() : OptionalInt.of(version);
    }

    private static VaultSecretsKV2ReadSecretMetadataVersion versionMetadata(
        VaultSecretsKV2ReadSecretMetadataResultData metadata,
        int version
    ) {
        Map<String, VaultSecretsKV2ReadSecretMetadataVersion> versions = metadata.getVersions();
        VaultSecretsKV2ReadSecretMetadataVersion current = versions == null ? null : versions.get(Integer.toString(version));
        if (current == null)
            throw malformedMetadata();
        return current;
    }

    private static boolean isDeleted(VaultSecretsKV2ReadSecretMetadataVersion version)
    {
        Boolean destroyed = version.isDestroyed();
        if (destroyed == null)
            throw malformedMetadata();
        return destroyed || version.getDeletionTime() != null;
    }

    record ActiveVersion(
        int number,
        VaultSecretsKV2ReadSecretMetadataVersion metadata
    ) {}
}
