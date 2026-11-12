package tech.illuin.wombat.engine.secret.vault;

import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretData;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretMetadataResultData;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretMetadataVersion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import tech.illuin.wombat.core.secret.MissingSecretException;
import tech.illuin.wombat.core.secret.SecretMetadata;
import tech.illuin.wombat.engine.secret.SecretProperties;
import tech.illuin.wombat.engine.secret.SecretStore;
import tech.illuin.wombat.engine.secret.vault.client.VaultKvV2Client;
import tech.illuin.wombat.engine.secret.vault.client.VaultPath;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;

@ApplicationScoped
@Typed(VaultSecretStore.class)
public class VaultSecretStore implements SecretStore
{
    private final VaultKvV2Client vault;
    private final String pathPrefix;

    public VaultSecretStore(VaultKvV2Client vault, SecretProperties properties)
    {
        this.vault = vault;
        this.pathPrefix = VaultPath.validatePrefix(properties.vault().pathPrefix());
    }

    @Override
    public Optional<String> find(String key)
    {
        String path = VaultPath.keyPath(this.pathPrefix, key);
        Optional<ActiveVersion> activeVersion = this.vault.readSecretMetadata(path)
            .flatMap(VaultSecretStore::activeCurrentVersion);
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
        return find(key).orElseThrow(() -> new MissingSecretException(key));
    }

    @Override
    public void putSecret(String key, String value)
    {
        String path = VaultPath.keyPath(this.pathPrefix, key);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Secret value must not be null or blank");

        this.vault.writeSecret(path, value);
    }

    @Override
    public boolean deleteSecret(String key)
    {
        String path = VaultPath.keyPath(this.pathPrefix, key);
        Optional<ActiveVersion> activeVersion = this.vault.readSecretMetadata(path)
            .flatMap(VaultSecretStore::activeCurrentVersion);
        if (activeVersion.isEmpty())
            return false;

        this.vault.deleteSecretVersion(path, activeVersion.orElseThrow().number());
        return true;
    }

    @Override
    public List<SecretMetadata> listSecrets()
    {
        Map<String, SecretMetadata> metadataByKey = new TreeMap<>();
        ArrayDeque<String> directories = new ArrayDeque<>();
        directories.add(this.pathPrefix);

        while (!directories.isEmpty())
        {
            String directoryPath = directories.removeFirst();
            List<String> listedKeys = this.vault.listSecrets(directoryPath);
            if (listedKeys == null)
                throw malformedListResponse();

            for (String listedKey : listedKeys)
            {
                if (listedKey == null || listedKey.isEmpty())
                    throw malformedListResponse();

                boolean isDirectory = listedKey.endsWith("/");
                String segment = isDirectory ? listedKey.substring(0, listedKey.length() - 1) : listedKey;
                if (!VaultPath.isValidSegment(segment))
                    throw malformedListResponse();

                String childPath = directoryPath + "/" + segment;
                String key = childPath.substring(this.pathPrefix.length() + 1);

                if (isDirectory)
                    directories.addLast(childPath);
                else
                    this.vault.readSecretMetadata(childPath).flatMap(value -> activeMetadata(key, value))
                        .ifPresent(value -> metadataByKey.put(key, value));
            }
        }
        return new ArrayList<>(metadataByKey.values());
    }

    private Optional<SecretMetadata> activeMetadata(String key, VaultSecretsKV2ReadSecretMetadataResultData metadata)
    {
        Optional<ActiveVersion> activeVersion = activeCurrentVersion(metadata);
        if (activeVersion.isEmpty())
            return Optional.empty();

        VaultSecretsKV2ReadSecretMetadataVersion version = activeVersion.orElseThrow().metadata();
        if (metadata.getCreatedTime() == null || version.getCreatedTime() == null)
            throw malformedMetadata();

        return Optional.of(new SecretMetadata(
            key,
            metadata.getCreatedTime().toInstant(),
            version.getCreatedTime().toInstant()
        ));
    }

    private static Optional<ActiveVersion> activeCurrentVersion(VaultSecretsKV2ReadSecretMetadataResultData metadata)
    {
        OptionalInt versionNumber = currentVersion(metadata);
        if (versionNumber.isEmpty())
            return Optional.empty();

        int version = versionNumber.getAsInt();
        VaultSecretsKV2ReadSecretMetadataVersion versionMetadata = versionMetadata(metadata, version);
        return isDeleted(versionMetadata) ? Optional.empty() : Optional.of(new ActiveVersion(version, versionMetadata));
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

    private static IllegalStateException malformedMetadata()
    {
        return new IllegalStateException("Vault returned malformed metadata for a secret path");
    }

    private static IllegalStateException malformedListResponse()
    {
        return new IllegalStateException("Vault returned a malformed KV v2 list response");
    }

    private record ActiveVersion(
        int number,
        VaultSecretsKV2ReadSecretMetadataVersion metadata
    ) {}
}
