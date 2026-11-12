package tech.illuin.wombat.engine.secret.vault.client;

import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretData;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretMetadataResultData;

import java.util.List;
import java.util.Optional;

public interface VaultKvV2Client
{
    List<String> listSecrets(String path);

    Optional<VaultSecretsKV2ReadSecretMetadataResultData> readSecretMetadata(String path);

    VaultSecretsKV2ReadSecretData readSecret(String path, int version);

    void writeSecret(String path, String value);

    void deleteSecretVersion(String path, int version);
}
