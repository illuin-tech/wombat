package tech.illuin.wombat.context;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.illuin.wombat.context.persistence.*;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIServerProfile;
import tech.illuin.wombat.core.asset.profile.ServerProvider;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIAsset;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.monitor.MonitoredEnvironments;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnvironmentReconcilerTest
{

    private final EnvironmentRepository environments = mock(EnvironmentRepository.class);
    private final AssetRepository assets = mock(AssetRepository.class);
    private final AssetConfigHistoryRepository history = mock(AssetConfigHistoryRepository.class);

    @Test
    void createsMissingEnvironmentAndAsset_withCreateHistory()
    {
        // Everything unstubbed: Mockito returns Optional.empty()/empty lists, i.e. a fresh DB.
        this.reconcile(desired("e1", "Env 1", k8s("a1", 43800)));

        verify(this.environments).persist(any(EnvironmentEntity.class));
        verify(this.assets).persist(any(AssetEntity.class));
        assertEquals(AssetConfigAction.CREATE, this.capturedAction());
    }

    @Test
    void updatesChangedAsset_withUpdateHistory()
    {
        AssetEntity existing = AssetEntity.from("e1", k8s("a1", 43800));
        when(this.assets.findByIdOptional("a1")).thenReturn(Optional.of(existing));

        this.reconcile(desired("e1", "Env 1", k8s("a1", 26280))); // lifespan changed

        assertEquals(AssetConfigAction.UPDATE, this.capturedAction());
        assertEquals(26280, ((KubernetesAPIAsset) existing.properties).profile().lifespan());
    }

    @Test
    void leavesUnchangedAssetUntouched_withNoHistory()
    {
        AssetEntity existing = AssetEntity.from("e1", k8s("a1", 43800));
        when(this.assets.findByIdOptional("a1")).thenReturn(Optional.of(existing));

        this.reconcile(desired("e1", "Env 1", k8s("a1", 43800))); // identical config

        verify(this.history, never()).persist(any(AssetConfigHistory.class));
    }

    @Test
    void undeletesReturningAsset_withCreateHistory()
    {
        AssetEntity existing = AssetEntity.from("e1", k8s("a1", 43800));
        existing.deletedAt = EnvironmentEntity.now();
        when(this.assets.findByIdOptional("a1")).thenReturn(Optional.of(existing));

        this.reconcile(desired("e1", "Env 1", k8s("a1", 43800)));

        assertNull(existing.deletedAt);
        assertEquals(AssetConfigAction.CREATE, this.capturedAction());
    }

    @Test
    void softDeletesAssetAbsentFromYaml_withDeleteHistory()
    {
        AssetEntity orphan = AssetEntity.from("e1", k8s("gone", 43800));
        when(this.assets.list("deletedAt is null")).thenReturn(List.of(orphan));

        this.reconcile(desired("e1", "Env 1")); // env kept, but no assets -> "gone" must be removed

        assertNotNull(orphan.deletedAt);
        assertEquals(AssetConfigAction.DELETE, this.capturedAction());
    }

    @Test
    void softDeletesUnrecognizedAssetAbsentFromYaml_withDeleteHistory()
    {
        String rawJson = "{\"id\":\"unrec-gone\",\"environment-id\":\"e1\",\"name\":\"Gone Asset\",\"type\":\"tech.illuin.unknown\"}";
        tech.illuin.wombat.context.model.UnrecognizedAsset unrecognized = new tech.illuin.wombat.context.model.UnrecognizedAsset(
            "unrec-gone", "e1", "Gone Asset", "tech.illuin.unknown", rawJson
        );
        AssetEntity orphan = new AssetEntity();
        orphan.id = "unrec-gone";
        orphan.environmentId = "e1";
        orphan.name = "Gone Asset";
        orphan.type = "tech.illuin.unknown";
        orphan.properties = unrecognized;

        when(this.assets.list("deletedAt is null")).thenReturn(List.of(orphan));

        this.reconcile(desired("e1", "Env 1"));

        assertNotNull(orphan.deletedAt);
        assertEquals(AssetConfigAction.DELETE, this.capturedAction());
    }

    @Test
    void updatesUnrecognizedAsset_withValidAssetFromYaml()
    {
        String rawJson = "{\"id\":\"a1\",\"environment-id\":\"e1\",\"name\":\"Old Unrecognized\",\"type\":\"tech.illuin.unknown\"}";
        tech.illuin.wombat.context.model.UnrecognizedAsset unrecognized = new tech.illuin.wombat.context.model.UnrecognizedAsset(
            "a1", "e1", "Old Unrecognized", "tech.illuin.unknown", rawJson
        );
        AssetEntity existing = new AssetEntity();
        existing.id = "a1";
        existing.environmentId = "e1";
        existing.name = "Old Unrecognized";
        existing.type = "tech.illuin.unknown";
        existing.properties = unrecognized;

        when(this.assets.findByIdOptional("a1")).thenReturn(Optional.of(existing));

        this.reconcile(desired("e1", "Env 1", k8s("a1", 43800)));

        assertEquals(AssetConfigAction.UPDATE, this.capturedAction());
        assertEquals(KubernetesAPIModule.TYPE.name(), existing.type);
        assertEquals(k8s("a1", 43800), existing.properties);
    }

    @Test
    void reEnablesReturningEnvironmentAndDisablesEnvironmentsAbsentFromYaml()
    {
        EnvironmentEntity returning = new EnvironmentEntity();
        returning.id = "e1";
        returning.name = "Env 1";
        returning.disabledAt = EnvironmentEntity.now();
        when(this.environments.findByIdOptional("e1")).thenReturn(Optional.of(returning));

        EnvironmentEntity orphan = new EnvironmentEntity();
        orphan.id = "old";
        orphan.name = "Old";
        when(this.environments.findActive()).thenReturn(List.of(orphan));

        this.reconcile(desired("e1", "Env 1")); // no assets

        assertNull(returning.disabledAt);
        assertNotNull(orphan.disabledAt);
    }

    private void reconcile(Map<String, Environment> desired)
    {
        new EnvironmentReconciler(this.environments, this.assets, this.history, new MonitoredEnvironments(desired))
            .onStart(null);
    }

    private AssetConfigAction capturedAction()
    {
        ArgumentCaptor<AssetConfigHistory> captor = ArgumentCaptor.forClass(AssetConfigHistory.class);
        verify(this.history).persist(captor.capture());
        return captor.getValue().action;
    }

    private static Map<String, Environment> desired(String envId, String name, Asset... assetProps)
    {
        return Map.of(envId, new Environment(name, List.of(assetProps)));
    }

    private static KubernetesAPIAsset k8s(String id, int lifespan)
    {
        return new KubernetesAPIAsset(id, "e1", id, "/kube/config", "ns", Optional.empty(), Optional.empty(), 0,
            new KubernetesAPIServerProfile(ServerProvider.aws, "c5.large", "FRA", lifespan));
    }
}
