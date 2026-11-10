package tech.illuin.wombat.engine.secret.vault;

import io.quarkus.vault.client.VaultClientException;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretData;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretMetadataResultData;
import io.quarkus.vault.client.api.secrets.kv2.VaultSecretsKV2ReadSecretMetadataVersion;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.secret.MissingSecretException;
import tech.illuin.wombat.engine.secret.SecretProperties;
import tech.illuin.wombat.engine.secret.vault.client.VaultInvalidKeyException;
import tech.illuin.wombat.engine.secret.vault.client.VaultKvV2Client;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VaultSecretStoreTest
{
    private final VaultKvV2Client vault = mock(VaultKvV2Client.class);
    private final VaultSecretStore store = new VaultSecretStore(this.vault, properties("wombat"));

    @Test
    void readsTheVersionObservedInMetadata()
    {
        when(this.vault.readSecretMetadata("wombat/team/api-key")).thenReturn(metadata(2, Map.of(
            "1", activeVersion(),
            "2", activeVersion()
        )));
        when(this.vault.readSecret("wombat/team/api-key", 2)).thenReturn(secret("secret-value"));

        assertEquals("secret-value", this.store.find("team/api-key").orElseThrow());

        verify(this.vault).readSecret("wombat/team/api-key", 2);
    }

    @Test
    void returnsEmptyForAnExplicitlyMissingKey()
    {
        when(this.vault.readSecretMetadata("wombat/missing")).thenReturn(Optional.empty());

        assertTrue(this.store.find("missing").isEmpty());
        assertThrows(MissingSecretException.class, () -> this.store.require("missing"));
    }


    @Test
    void returnsEmptyForDeletedOrDestroyedCurrentVersions()
    {
        when(this.vault.readSecretMetadata("wombat/deleted")).thenReturn(metadata(2, Map.of(
            "1", activeVersion(),
            "2", deletedVersion()
        )));
        when(this.vault.readSecretMetadata("wombat/destroyed")).thenReturn(metadata(3, Map.of(
            "3", destroyedVersion()
        )));

        assertTrue(this.store.find("deleted").isEmpty());
        assertTrue(this.store.find("destroyed").isEmpty());
        verify(this.vault, never()).readSecret("wombat/deleted", 2);
        verify(this.vault, never()).readSecret("wombat/destroyed", 3);
    }

    @Test
    void treatsMetadataWithoutAnyVersionAsAnAbsentSecret()
    {
        when(this.vault.readSecretMetadata("wombat/empty")).thenReturn(metadata(0, Map.of()));

        assertTrue(this.store.find("empty").isEmpty());
        assertFalse(this.store.deleteSecret("empty"));
        verify(this.vault, never()).readSecret(anyString(), anyInt());
    }

    @Test
    void doesNotHideMountOrAuthorizationFailuresAsMissingKeys()
    {
        VaultClientException mountFailure = new VaultClientException(
            "readSecretMetadata", "http://vault/v1/secret/metadata/wombat/missing", 404, List.of("no handler for route"), null
        );
        when(this.vault.readSecretMetadata("wombat/missing")).thenThrow(mountFailure);

        assertThrows(VaultClientException.class, () -> this.store.find("missing"));
    }

    @Test
    void rejectsMalformedVaultEntriesInsteadOfFallingBack()
    {
        when(this.vault.readSecretMetadata("wombat/missing-value")).thenReturn(metadata(1, Map.of("1", activeVersion())));
        when(this.vault.readSecret("wombat/missing-value", 1)).thenReturn(new VaultSecretsKV2ReadSecretData().setData(Map.of()));
        when(this.vault.readSecretMetadata("wombat/blank-value")).thenReturn(metadata(1, Map.of("1", activeVersion())));
        when(this.vault.readSecret("wombat/blank-value", 1)).thenReturn(secret(" "));
        when(this.vault.readSecretMetadata("wombat/non-string-value")).thenReturn(metadata(1, Map.of("1", activeVersion())));
        when(this.vault.readSecret("wombat/non-string-value", 1)).thenReturn(
            new VaultSecretsKV2ReadSecretData().setData(Map.of("value", 42))
        );

        assertThrows(IllegalStateException.class, () -> this.store.find("missing-value"));
        assertThrows(IllegalStateException.class, () -> this.store.find("blank-value"));
        assertThrows(IllegalStateException.class, () -> this.store.find("non-string-value"));
    }

    @Test
    void propagatesNonNotFoundFailures()
    {
        VaultClientException permissionFailure = new VaultClientException(
            "readSecretMetadata", "http://vault/v1/secret/metadata/wombat/key", 403, List.of("permission denied"), null
        );
        when(this.vault.readSecretMetadata("wombat/key")).thenThrow(permissionFailure);

        assertThrows(VaultClientException.class, () -> this.store.find("key"));
    }

    @Test
    void writesOneValueFieldAtTheNestedVaultPath()
    {
        this.store.putSecret("team/api-key", "secret-value");

        verify(this.vault).writeSecret("wombat/team/api-key", "secret-value");
    }

    @Test
    void rejectsInvalidKeysAndBlankValuesBeforeMakingVaultRequests()
    {
        assertThrows(VaultInvalidKeyException.class, () -> this.store.putSecret("../outside", "secret-value"));
        assertThrows(VaultInvalidKeyException.class, () -> this.store.find("a?b"));
        assertThrows(IllegalArgumentException.class, () -> this.store.putSecret("valid/key", " "));

        verifyNoInteractions(this.vault);
    }

    @Test
    void softDeletesOnlyTheCurrentObservedVersion()
    {
        when(this.vault.readSecretMetadata("wombat/key")).thenReturn(metadata(2, Map.of(
            "1", activeVersion(),
            "2", activeVersion()
        )));

        assertTrue(this.store.deleteSecret("key"));

        verify(this.vault).deleteSecretVersion("wombat/key", 2);
        verify(this.vault, never()).deleteSecretVersion("wombat/key", 1);
    }

    @Test
    void doesNotDeleteAnAbsentOrAlreadyDeletedVersion()
    {
        when(this.vault.readSecretMetadata("wombat/absent")).thenReturn(Optional.empty());
        when(this.vault.readSecretMetadata("wombat/deleted")).thenReturn(metadata(2, Map.of("2", deletedVersion())));

        assertFalse(this.store.deleteSecret("absent"));
        assertFalse(this.store.deleteSecret("deleted"));
        verify(this.vault, never()).deleteSecretVersion("wombat/absent", 1);
        verify(this.vault, never()).deleteSecretVersion("wombat/deleted", 2);
    }

    @Test
    void recursivelyListsOnlyActiveMetadataInSortedOrderIncludingLeafAndDirectoryOfSameName()
    {
        when(this.vault.listSecrets("wombat")).thenReturn(List.of("z", "shared", "shared/", "team/"));
        when(this.vault.listSecrets("wombat/shared")).thenReturn(List.of("child"));
        when(this.vault.listSecrets("wombat/team")).thenReturn(List.of("a", "deleted", "empty/"));
        when(this.vault.listSecrets("wombat/team/empty")).thenReturn(List.of());
        when(this.vault.readSecretMetadata("wombat/z")).thenReturn(metadata(2, Map.of(
            "1", activeVersion(),
            "2", deletedVersion()
        )));
        when(this.vault.readSecretMetadata("wombat/shared")).thenReturn(metadata(1, Map.of("1", activeVersion())));
        when(this.vault.readSecretMetadata("wombat/shared/child")).thenReturn(metadata(1, Map.of("1", activeVersion())));
        when(this.vault.readSecretMetadata("wombat/team/a")).thenReturn(metadata(1, Map.of("1", activeVersion())));
        when(this.vault.readSecretMetadata("wombat/team/deleted")).thenReturn(metadata(3, Map.of("3", deletedVersion())));

        List<tech.illuin.wombat.core.secret.SecretMetadata> listed = this.store.listSecrets();

        assertEquals(List.of("shared", "shared/child", "team/a"), listed.stream().map(metadata -> metadata.key()).toList());
        assertEquals(OffsetDateTime.parse("2024-01-01T00:00:00Z").toInstant(), listed.getFirst().createdAt());
        assertEquals(OffsetDateTime.parse("2025-01-01T00:00:00Z").toInstant(), listed.getFirst().updatedAt());
        verify(this.vault, never()).readSecret(anyString(), anyInt());
    }

    @Test
    void treatsAnEmptyOrMissingListPrefixAsEmpty()
    {
        when(this.vault.listSecrets("wombat")).thenReturn(List.of());

        assertTrue(this.store.listSecrets().isEmpty());
    }


    @Test
    void propagatesListErrorsThatAreNotAKeyNotFound()
    {
        VaultClientException permissionFailure = new VaultClientException(
            "listSecrets", "http://vault/v1/secret/metadata/wombat", 403, List.of("permission denied"), null
        );
        when(this.vault.listSecrets("wombat")).thenThrow(permissionFailure);

        assertThrows(VaultClientException.class, () -> this.store.listSecrets());
    }

    @Test
    void rejectsMalformedListEntriesBeforeReadingMetadata()
    {
        when(this.vault.listSecrets("wombat")).thenReturn(List.of("../outside"));

        assertThrows(IllegalStateException.class, () -> this.store.listSecrets());

        when(this.vault.listSecrets("wombat")).thenReturn(List.of("nested/key"));
        assertThrows(IllegalStateException.class, () -> this.store.listSecrets());

        verify(this.vault, never()).readSecretMetadata(anyString());
    }

    private static SecretProperties properties(String prefix)
    {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
            .withMapping(SecretProperties.class)
            .withSources(new PropertiesConfigSource(Map.of(
                "wombat.secret.vault.path-prefix", prefix
            ), "test", 100))
            .build();
        return config.getConfigMapping(SecretProperties.class);
    }

    private static Optional<VaultSecretsKV2ReadSecretMetadataResultData> metadata(
        int currentVersion,
        Map<String, VaultSecretsKV2ReadSecretMetadataVersion> versions
    ) {
        return Optional.of(new VaultSecretsKV2ReadSecretMetadataResultData()
            .setCreatedTime(OffsetDateTime.parse("2024-01-01T00:00:00Z"))
            .setCurrentVersion(currentVersion)
            .setVersions(versions));
    }

    private static VaultSecretsKV2ReadSecretMetadataVersion activeVersion()
    {
        return new VaultSecretsKV2ReadSecretMetadataVersion()
            .setCreatedTime(OffsetDateTime.parse("2025-01-01T00:00:00Z"))
            .setDestroyed(false);
    }

    private static VaultSecretsKV2ReadSecretMetadataVersion deletedVersion()
    {
        return activeVersion().setDeletionTime(OffsetDateTime.now());
    }

    private static VaultSecretsKV2ReadSecretMetadataVersion destroyedVersion()
    {
        return activeVersion().setDestroyed(true);
    }

    private static VaultSecretsKV2ReadSecretData secret(String value)
    {
        return new VaultSecretsKV2ReadSecretData().setData(Map.of("value", value));
    }
}
