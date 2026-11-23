package tech.illuin.wombat.engine.secret.vault;

import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretMetadataResultData;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretMetadataVersion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import tech.illuin.wombat.core.secret.SecretMetadata;
import tech.illuin.wombat.engine.secret.SecretStore;
import tech.illuin.wombat.engine.secret.vault.VaultSecretResolver.ActiveVersion;
import tech.illuin.wombat.engine.secret.vault.client.VaultKvV2Client;
import tech.illuin.wombat.engine.secret.vault.client.VaultPath;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@ApplicationScoped
@Typed(VaultSecretStore.class)
public class VaultSecretStore implements SecretStore
{
    private final VaultKvV2Client vault;
    private final VaultSecretResolver resolver;
    private final String pathPrefix;

    public VaultSecretStore(VaultKvV2Client vault, VaultSecretResolver resolver)
    {
        this.vault = vault;
        this.resolver = resolver;
        this.pathPrefix = resolver.pathPrefix();
    }

    @Override
    public Optional<String> find(String key)
    {
        return this.resolver.find(key);
    }

    @Override
    public String require(String key)
    {
        return this.resolver.require(key);
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
        Optional<ActiveVersion> activeVersion = this.resolver.activeVersion(path);
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
        Optional<ActiveVersion> activeVersion = VaultSecretResolver.activeCurrentVersion(metadata);
        if (activeVersion.isEmpty())
            return Optional.empty();

        VaultSecretsKV2ReadSecretMetadataVersion version = activeVersion.orElseThrow().metadata();
        if (metadata.getCreatedTime() == null || version.getCreatedTime() == null)
            throw VaultSecretResolver.malformedMetadata();

        return Optional.of(new SecretMetadata(
            key,
            metadata.getCreatedTime().toInstant(),
            version.getCreatedTime().toInstant()
        ));
    }

    private static IllegalStateException malformedListResponse()
    {
        return new IllegalStateException("Vault returned a malformed KV v2 list response");
    }
}
