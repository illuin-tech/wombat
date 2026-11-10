package tech.illuin.wombat.engine.secret.vault.client;

import io.quarkus.vault.client.VaultClient;
import io.quarkus.vault.client.VaultClientException;
import io.quarkus.vault.client.api.VaultSecretsAccessor;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ListSecretsResult;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ListSecretsResultData;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretData;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretMetadataResultData;
import io.quarkus.vault.client.common.VaultRequest;
import io.quarkus.vault.client.common.VaultResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VaultKvV2ClientAdapterTest
{
    @Test
    void usesTheConfiguredMountThroughTheSharedVaultClient()
    {
        VaultClient client = mock(VaultClient.class);
        VaultSecretsAccessor secrets = mock(VaultSecretsAccessor.class);
        VaultSecretsKV2 kv2 = mock(VaultSecretsKV2.class);
        VaultSecretsKV2ReadSecretMetadataResultData metadata = new VaultSecretsKV2ReadSecretMetadataResultData();
        when(client.secrets()).thenReturn(secrets);
        when(secrets.kv2("applications")).thenReturn(kv2);
        VaultSecretsKV2ListSecretsResult listResult = new VaultSecretsKV2ListSecretsResult();
        listResult.setData(new VaultSecretsKV2ListSecretsResultData().setKeys(List.of("key")));
        VaultResponse<VaultSecretsKV2ListSecretsResult> response = mock(VaultResponse.class);
        when(response.getResult()).thenReturn(listResult);
        when(client.<VaultSecretsKV2ListSecretsResult>execute(any())).thenReturn(CompletableFuture.completedFuture(response));
        when(kv2.readSecretMetadata("wombat/key")).thenReturn(CompletableFuture.completedFuture(metadata));
        when(kv2.updateSecret(eq("wombat/key"), any(), eq(Map.of("value", "secret"))))
            .thenReturn(CompletableFuture.completedFuture(new VaultSecretsKV2ReadSecretData()));
        when(kv2.deleteSecretVersions("wombat/key", List.of(3))).thenReturn(CompletableFuture.completedFuture(null));

        VaultKvV2ClientAdapter adapter = new VaultKvV2ClientAdapter(client, "applications");
        assertEquals(List.of("key"), adapter.listSecrets("wombat"));
        assertSame(metadata, adapter.readSecretMetadata("wombat/key").orElseThrow());
        adapter.writeSecret("wombat/key", "secret");
        adapter.deleteSecretVersion("wombat/key", 3);

        verify(secrets).kv2("applications");
        verify(client).execute(argThat((VaultRequest<?> request) -> request.getMethod() == VaultRequest.Method.LIST
            && request.getPath().endsWith("applications/metadata/wombat")));
        verify(kv2).readSecretMetadata("wombat/key");
        verify(kv2).updateSecret(eq("wombat/key"), any(), eq(Map.of("value", "secret")));
        verify(kv2).deleteSecretVersions("wombat/key", List.of(3));
    }

    @Test
    void normalizesTypedAndRawMissingMetadata404Responses()
    {
        VaultClient client = mock(VaultClient.class);
        VaultSecretsAccessor secrets = mock(VaultSecretsAccessor.class);
        VaultSecretsKV2 kv2 = mock(VaultSecretsKV2.class);
        when(client.secrets()).thenReturn(secrets);
        when(secrets.kv2("applications")).thenReturn(kv2);

        VaultClientException typedMissing = new VaultClientException(
            "readSecretMetadata", "http://vault/v1/secret/metadata/wombat/typed", 404, List.of(), null
        );
        VaultClientException rawMissing = new VaultClientException(
            "readSecretMetadata", "http://vault/v1/secret/metadata/wombat/raw", 404, "{\"errors\":[]}", null
        );
        VaultClientException rawMessageMissing = new VaultClientException(
            "readSecretMetadata", "http://vault/v1/secret/metadata/wombat/message", 404,
            "{\"errors\":[\"no value found at secret/metadata/wombat/message\"]}", null
        );
        when(kv2.readSecretMetadata("wombat/typed")).thenReturn(CompletableFuture.failedFuture(typedMissing));
        when(kv2.readSecretMetadata("wombat/raw")).thenReturn(CompletableFuture.failedFuture(rawMissing));
        when(kv2.readSecretMetadata("wombat/message")).thenReturn(CompletableFuture.failedFuture(rawMessageMissing));

        VaultKvV2ClientAdapter adapter = new VaultKvV2ClientAdapter(client, "applications");

        assertEquals(Optional.empty(), adapter.readSecretMetadata("wombat/typed"));
        assertEquals(Optional.empty(), adapter.readSecretMetadata("wombat/raw"));
        assertEquals(Optional.empty(), adapter.readSecretMetadata("wombat/message"));
    }

    @Test
    void normalizesMissingDirectoryButPropagatesInvalidRoute404()
    {
        VaultClient client = mock(VaultClient.class);
        VaultSecretsAccessor secrets = mock(VaultSecretsAccessor.class);
        VaultClientException missingDirectory = new VaultClientException(
            "listSecrets", "http://vault/v1/secret/metadata/wombat/missing", 404, List.of(), null
        );
        VaultClientException invalidRoute = new VaultClientException(
            "listSecrets", "http://vault/v1/secret/metadata/wombat/invalid", 404, List.of("no handler for route"), null
        );
        when(client.secrets()).thenReturn(secrets);
        when(secrets.kv2("applications")).thenReturn(mock(VaultSecretsKV2.class));
        when(client.<VaultSecretsKV2ListSecretsResult>execute(any())).thenReturn(
            CompletableFuture.failedFuture(missingDirectory),
            CompletableFuture.failedFuture(invalidRoute)
        );

        VaultKvV2ClientAdapter adapter = new VaultKvV2ClientAdapter(client, "applications");

        assertEquals(List.of(), adapter.listSecrets("wombat/missing"));
        assertSame(invalidRoute, assertThrows(VaultClientException.class, () -> adapter.listSecrets("wombat/invalid")));
    }

    @Test
    void propagatesMalformedAndUnrecognized404Bodies()
    {
        VaultClient client = mock(VaultClient.class);
        VaultSecretsAccessor secrets = mock(VaultSecretsAccessor.class);
        VaultSecretsKV2 kv2 = mock(VaultSecretsKV2.class);
        when(client.secrets()).thenReturn(secrets);
        when(secrets.kv2("applications")).thenReturn(kv2);

        VaultClientException malformed = new VaultClientException(
            "readSecretMetadata", "http://vault/v1/secret/metadata/wombat/malformed", 404, "not-json", null
        );
        VaultClientException unknown = new VaultClientException(
            "readSecretMetadata", "http://vault/v1/secret/metadata/wombat/unknown", 404,
            "{\"errors\":[\"invalid route\"]}", null
        );
        when(kv2.readSecretMetadata("wombat/malformed")).thenReturn(CompletableFuture.failedFuture(malformed));
        when(kv2.readSecretMetadata("wombat/unknown")).thenReturn(CompletableFuture.failedFuture(unknown));

        VaultKvV2ClientAdapter adapter = new VaultKvV2ClientAdapter(client, "applications");

        assertSame(malformed, assertThrows(VaultClientException.class, () -> adapter.readSecretMetadata("wombat/malformed")));
        assertSame(unknown, assertThrows(VaultClientException.class, () -> adapter.readSecretMetadata("wombat/unknown")));
    }

    @Test
    void propagatesAuthorizationAndTransportFailures()
    {
        VaultClient client = mock(VaultClient.class);
        VaultSecretsAccessor secrets = mock(VaultSecretsAccessor.class);
        VaultSecretsKV2 kv2 = mock(VaultSecretsKV2.class);
        when(client.secrets()).thenReturn(secrets);
        when(secrets.kv2("applications")).thenReturn(kv2);

        VaultClientException denied = new VaultClientException(
            "readSecretMetadata", "http://vault/v1/secret/metadata/wombat/denied", 403, List.of("permission denied"), null
        );
        IllegalStateException transport = new IllegalStateException("transport failure");
        when(kv2.readSecretMetadata("wombat/denied")).thenReturn(CompletableFuture.failedFuture(denied));
        when(kv2.readSecretMetadata("wombat/transport")).thenReturn(CompletableFuture.failedFuture(transport));

        VaultKvV2ClientAdapter adapter = new VaultKvV2ClientAdapter(client, "applications");

        assertSame(denied, assertThrows(VaultClientException.class, () -> adapter.readSecretMetadata("wombat/denied")));
        assertSame(transport, assertThrows(IllegalStateException.class, () -> adapter.readSecretMetadata("wombat/transport")));
    }

    @Test
    void doesNotCallVaultForAnInvalidConfiguredMount()
    {
        VaultClient client = mock(VaultClient.class);

        assertThrows(IllegalStateException.class, () -> new VaultKvV2ClientAdapter(client, "../secret"));

        verifyNoInteractions(client);
    }
}
