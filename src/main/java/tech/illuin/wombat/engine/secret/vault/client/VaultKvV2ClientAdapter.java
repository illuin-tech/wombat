package tech.illuin.wombat.engine.secret.vault.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.quarkus.vault.client.VaultClient;
import io.quarkus.vault.client.VaultClientException;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ListSecretsResult;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ListSecretsResultData;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretData;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretMetadataResultData;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2RequestFactory;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2UpdateSecretOptions;
import io.quarkus.vault.client.common.VaultErrorResponse;
import io.quarkus.vault.client.common.VaultRequest;
import io.quarkus.vault.client.common.VaultResponse;
import io.quarkus.vault.client.json.JsonMapping;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class VaultKvV2ClientAdapter implements VaultKvV2Client
{
    private final VaultClient client;
    private final VaultSecretsKV2 secrets;
    private final String mountPath;

    public VaultKvV2ClientAdapter(
        VaultClient client,
        @ConfigProperty(name = "quarkus.vault.kv-secret-engine-mount-path", defaultValue = "secret") String mountPath
    ) {
        String validatedMountPath = VaultPath.validateMountPath(mountPath);
        this.client = client;
        this.secrets = client.secrets().kv2(validatedMountPath);
        this.mountPath = validatedMountPath;
    }

    @Override
    public List<String> listSecrets(String path)
    {
        try {
            VaultRequest<VaultSecretsKV2ListSecretsResult> request = VaultSecretsKV2RequestFactory.INSTANCE
                .listSecrets(this.mountPath, path);
            VaultResponse<VaultSecretsKV2ListSecretsResult> response = await(this.client.execute(request));
            VaultSecretsKV2ListSecretsResult result = response == null ? null : response.getResult();
            VaultSecretsKV2ListSecretsResultData data = result == null ? null : result.getData();
            if (data == null || data.getKeys() == null)
                throw new IllegalStateException("Vault returned a malformed KV v2 list response");
            return data.getKeys();
        }
        catch (VaultClientException exception) {
            if (isMissingPath(exception))
                return List.of();
            throw exception;
        }
    }

    @Override
    public Optional<VaultSecretsKV2ReadSecretMetadataResultData> readSecretMetadata(String path)
    {
        try {
            VaultSecretsKV2ReadSecretMetadataResultData metadata = await(this.secrets.readSecretMetadata(path));
            if (metadata == null)
                throw new IllegalStateException("Vault returned malformed metadata for a secret path");
            return Optional.of(metadata);
        }
        catch (VaultClientException exception) {
            if (isMissingPath(exception))
                return Optional.empty();
            throw exception;
        }
    }

    @Override
    public VaultSecretsKV2ReadSecretData readSecret(String path, int version)
    {
        return await(this.secrets.readSecret(path, version));
    }

    @Override
    public void writeSecret(String path, String value)
    {
        await(this.secrets.updateSecret(path, new VaultSecretsKV2UpdateSecretOptions(), Map.of("value", value)));
    }

    @Override
    public void deleteSecretVersion(String path, int version)
    {
        await(this.secrets.deleteSecretVersions(path, List.of(version)));
    }

    private static boolean isMissingPath(VaultClientException exception)
    {
        if (!Integer.valueOf(404).equals(exception.getStatus()))
            return false;

        List<String> errors = exception.getErrors();
        if (errors == null)
        {
            String body = exception.getBody();
            if (body == null)
                return false;
            try {
                VaultErrorResponse response = JsonMapping.mapper.readValue(body, VaultErrorResponse.class);
                errors = response == null ? null : response.errors();
            }
            catch (JsonProcessingException exceptionWhileParsing) {
                return false;
            }
        }

        return errors != null && (errors.isEmpty()
            || errors.stream().allMatch(error -> error != null && error.trim().startsWith("no value found at ")));
    }

    private static <T> T await(CompletionStage<T> request)
    {
        try {
            return request.toCompletableFuture().join();
        }
        catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            while (cause instanceof CompletionException && cause.getCause() != null)
                cause = cause.getCause();
            if (cause instanceof RuntimeException runtimeException)
                throw runtimeException;
            if (cause instanceof Error error)
                throw error;
            throw new IllegalStateException("Vault request failed", cause);
        }
    }
}
