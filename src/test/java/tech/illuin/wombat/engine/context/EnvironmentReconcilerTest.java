package tech.illuin.wombat.engine.context;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.illuin.wombat.engine.context.asset.AssetConflictException;
import tech.illuin.wombat.engine.context.asset.AssetHistoryRecorder;
import tech.illuin.wombat.engine.context.asset.model.UnrecognizedAsset;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConfigAction;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConfigHistory;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConfigHistoryRepository;
import tech.illuin.wombat.engine.context.asset.persistence.AssetEntity;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.environment.EnvironmentConflictException;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentEntity;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentRepository;
import tech.illuin.wombat.core.asset.profile.Lifetime;
import tech.illuin.wombat.core.source.Period;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIServerProfile;
import tech.illuin.wombat.core.asset.profile.ServerProvider;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIAsset;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.engine.monitor.MonitoredEnvironments;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.illuin.wombat.core.activity.commons.Country.FRANCE;

class EnvironmentReconcilerTest
{
    private final EnvironmentRepository environments = mock(EnvironmentRepository.class);
    private final AssetRepository assets = mock(AssetRepository.class);
    private final AssetConfigHistoryRepository history = mock(AssetConfigHistoryRepository.class);

    @Test
    void createsMissingEnvironmentAndAsset_withCreateHistory()
    {
        // Everything unstubbed: Mockito returns Optional.empty()/empty lists, i.e. a fresh DB.
        this.reconcile(desired("e1", "Env 1", k8s("a1", Lifetime.ofYears(5))));

        verify(this.environments).persist(any(EnvironmentEntity.class));
        verify(this.assets).persist(any(AssetEntity.class));
        assertEquals(AssetConfigAction.CREATE, this.capturedAction());
    }

    @Test
    void updatesChangedAsset_withUpdateHistory()
    {
        AssetEntity existing = AssetEntity.from("e1", k8s("a1", Lifetime.ofYears(5)));
        when(this.assets.findByIdOptional("a1")).thenReturn(Optional.of(existing));

        this.reconcile(desired("e1", "Env 1", k8s("a1", Lifetime.ofYears(3)))); // lifespan changed

        assertEquals(AssetConfigAction.UPDATE, this.capturedAction());
        assertEquals("3y", ((KubernetesAPIAsset) existing.properties).profile().lifespan().asText());
    }

    @Test
    void leavesUnchangedAssetUntouched_withNoHistory()
    {
        AssetEntity existing = AssetEntity.from("e1", k8s("a1", Lifetime.ofYears(5)));
        when(this.assets.findByIdOptional("a1")).thenReturn(Optional.of(existing));

        this.reconcile(desired("e1", "Env 1", k8s("a1", Lifetime.ofYears(5)))); // identical config

        verify(this.history, never()).persist(any(AssetConfigHistory.class));
    }

    @Test
    void undeletesReturningAsset_withCreateHistory()
    {
        AssetEntity existing = AssetEntity.from("e1", k8s("a1", Lifetime.ofYears(5)));
        existing.deletedAt = EnvironmentEntity.now();
        when(this.assets.findByIdOptional("a1")).thenReturn(Optional.of(existing));

        this.reconcile(desired("e1", "Env 1", k8s("a1", Lifetime.ofYears(5))));

        assertNull(existing.deletedAt);
        assertEquals(AssetConfigAction.CREATE, this.capturedAction());
    }

    @Test
    void softDeletesAssetAbsentFromYaml_withDeleteHistory()
    {
        AssetEntity orphan = AssetEntity.from("e1", k8s("gone", Lifetime.ofYears(5)));
        when(this.assets.findVisible()).thenReturn(List.of(orphan));

        this.reconcile(desired("e1", "Env 1")); // env kept, but no assets -> "gone" must be removed

        assertNotNull(orphan.deletedAt);
        assertEquals(AssetConfigAction.DELETE, this.capturedAction());
    }

    @Test
    void softDeletesUnrecognizedAssetAbsentFromYaml_withDeleteHistory()
    {
        String rawJson = "{\"id\":\"unrec-gone\",\"environment-id\":\"e1\",\"name\":\"Gone Asset\",\"type\":\"tech.illuin.unknown\"}";
        UnrecognizedAsset unrecognized = new UnrecognizedAsset(AssetIdentity.of("unrec-gone", "e1", "Gone Asset", "tech.illuin.unknown"), rawJson
        );
        AssetEntity orphan = new AssetEntity();
        orphan.id = "unrec-gone";
        orphan.environmentId = "e1";
        orphan.name = "Gone Asset";
        orphan.type = "tech.illuin.unknown";
        orphan.properties = unrecognized;
        orphan.provisioned = true;

        when(this.assets.findVisible()).thenReturn(List.of(orphan));

        this.reconcile(desired("e1", "Env 1"));

        assertNotNull(orphan.deletedAt);
        assertEquals(AssetConfigAction.DELETE, this.capturedAction());
    }

    @Test
    void updatesUnrecognizedAsset_withValidAssetFromYaml()
    {
        String rawJson = "{\"id\":\"a1\",\"environment-id\":\"e1\",\"name\":\"Old Unrecognized\",\"type\":\"tech.illuin.unknown\"}";
        UnrecognizedAsset unrecognized = new UnrecognizedAsset(AssetIdentity.of("a1", "e1", "Old Unrecognized", "tech.illuin.unknown"), rawJson
        );
        AssetEntity existing = new AssetEntity();
        existing.id = "a1";
        existing.environmentId = "e1";
        existing.name = "Old Unrecognized";
        existing.type = "tech.illuin.unknown";
        existing.properties = unrecognized;
        existing.provisioned = true;

        when(this.assets.findByIdOptional("a1")).thenReturn(Optional.of(existing));

        this.reconcile(desired("e1", "Env 1", k8s("a1", Lifetime.ofYears(5))));

        assertEquals(AssetConfigAction.UPDATE, this.capturedAction());
        assertEquals(KubernetesAPIModule.TYPE.name(), existing.type);
        assertEquals(k8s("a1", Lifetime.ofYears(5)), existing.properties);
    }

    @Test
    void restoresReturningEnvironmentAndSoftDeletesEnvironmentsAbsentFromYaml()
    {
        EnvironmentEntity returning = provisioned("e1", "Env 1");
        returning.deletedAt = EnvironmentEntity.now();
        when(this.environments.findByIdOptional("e1")).thenReturn(Optional.of(returning));

        EnvironmentEntity orphan = provisioned("old", "Old");
        when(this.environments.findVisible()).thenReturn(List.of(orphan));

        this.reconcile(desired("e1", "Env 1")); // no assets

        assertNull(returning.deletedAt);
        assertNotNull(orphan.deletedAt);
    }

    /* Ownership: a worker's declaration and the YAML each manage their own assets of a shared environment. */

    @Test
    void worker_createsItsAssetsAsTheirOwner()
    {
        AssetEntity created = this.reconcileAsWorker("east", desired("e1", "Env 1", k8s("a1", Lifetime.ofYears(5))));

        assertEquals("east", created.owner);
    }

    @Test
    void worker_cannotDeclareAnAssetOwnedByTheYaml()
    {
        AssetEntity local = AssetEntity.from("e1", k8s("a1", Lifetime.ofYears(5)));
        when(this.assets.findByIdOptional("a1")).thenReturn(Optional.of(local));

        assertThrows(AssetConflictException.class, () -> this.reconcileAsWorker("east", desired("e1", "Env 1", k8s("a1", Lifetime.ofYears(5)))));
        assertNull(local.owner);
    }

    @Test
    void worker_cannotDeclareAnAssetOwnedByAnotherWorker()
    {
        AssetEntity other = owned("west", AssetEntity.from("e1", k8s("a1", Lifetime.ofYears(5))));
        when(this.assets.findByIdOptional("a1")).thenReturn(Optional.of(other));

        assertThrows(AssetConflictException.class, () -> this.reconcileAsWorker("east", desired("e1", "Env 1", k8s("a1", Lifetime.ofYears(5)))));
    }

    @Test
    void worker_takesOverADeletedAsset()
    {
        AssetEntity deleted = AssetEntity.from("e1", k8s("a1", Lifetime.ofYears(5)));
        deleted.deletedAt = EnvironmentEntity.now();
        when(this.assets.findByIdOptional("a1")).thenReturn(Optional.of(deleted));

        this.reconcileAsWorker("east", desired("e1", "Env 1", k8s("a1", Lifetime.ofYears(5))));

        assertEquals("east", deleted.owner);
        assertNull(deleted.deletedAt);
    }

    @Test
    void worker_onlyDeletesItsOwnAssets()
    {
        AssetEntity local = AssetEntity.from("e1", k8s("local", Lifetime.ofYears(5)));
        AssetEntity mine = owned("east", AssetEntity.from("e1", k8s("mine", Lifetime.ofYears(5))));
        AssetEntity theirs = owned("west", AssetEntity.from("e1", k8s("theirs", Lifetime.ofYears(5))));
        when(this.assets.findVisible()).thenReturn(List.of(local, mine, theirs));

        this.reconciler().reconcile("east", Map.of());

        assertNull(local.deletedAt);
        assertNotNull(mine.deletedAt);
        assertNull(theirs.deletedAt);
    }

    @Test
    void yaml_keepsTheAssetsOfWorkers_andTheEnvironmentsHoldingThem()
    {
        AssetEntity pushed = owned("east", AssetEntity.from("pushed-env", k8s("pushed", Lifetime.ofYears(5))));
        EnvironmentEntity pushedEnvironment = provisioned("pushed-env", "pushed-env");
        when(this.assets.findVisible()).thenReturn(List.of(pushed));
        when(this.environments.findVisible()).thenReturn(List.of(pushedEnvironment));

        this.reconcile(desired("e1", "Env 1"));

        assertNull(pushed.deletedAt);
        assertNull(pushedEnvironment.deletedAt);
    }

    @Test
    void yaml_takesOverTheAssetsItDeclares()
    {
        AssetEntity pushed = owned("east", AssetEntity.from("e1", k8s("a1", Lifetime.ofYears(5))));
        when(this.assets.findByIdOptional("a1")).thenReturn(Optional.of(pushed));

        this.reconcile(desired("e1", "Env 1", k8s("a1", Lifetime.ofYears(5))));

        assertNull(pushed.owner);
    }

    @Test
    void worker_softDeletesTheEnvironmentItEmptied_unlessTheYamlDeclaresIt()
    {
        AssetEntity gone = owned("east", AssetEntity.from("pushed-env", k8s("gone", Lifetime.ofYears(5))));
        AssetEntity alsoGone = owned("east", AssetEntity.from("e1", k8s("also-gone", Lifetime.ofYears(5))));
        EnvironmentEntity pushedEnvironment = provisioned("pushed-env", "pushed-env");
        EnvironmentEntity yamlEnvironment = provisioned("e1", "e1");
        when(this.assets.findVisible()).thenReturn(List.of(gone, alsoGone));
        when(this.environments.findVisible()).thenReturn(List.of(pushedEnvironment, yamlEnvironment));

        new EnvironmentReconciler(this.environments, this.assets, new AssetHistoryRecorder(this.history), new MonitoredEnvironments(desired("e1", "Env 1")))
            .reconcile("east", Map.of());

        assertNotNull(pushedEnvironment.deletedAt);
        assertNull(yamlEnvironment.deletedAt);
    }

    /* Environments and assets created through the API: never touched by a declaration, which must not clash with them. */

    @Test
    void yaml_failsOnAnEnvironmentCreatedThroughTheApi()
    {
        when(this.environments.findByIdOptional("e1")).thenReturn(Optional.of(created("e1")));

        assertThrows(EnvironmentConflictException.class, () -> this.reconcile(desired("e1", "Env 1")));
    }

    @Test
    void worker_failsOnAnEnvironmentCreatedThroughTheApi()
    {
        when(this.environments.findByIdOptional("e1")).thenReturn(Optional.of(created("e1")));

        assertThrows(EnvironmentConflictException.class, () -> this.reconciler().reconcile("east", desired("e1", "Env 1")));
    }

    @Test
    void yaml_failsOnAnAssetCreatedThroughTheApi()
    {
        AssetEntity user = created(AssetEntity.from("e1", k8s("a1", Lifetime.ofYears(5))));
        when(this.assets.findByIdOptional("a1")).thenReturn(Optional.of(user));

        assertThrows(AssetConflictException.class, () -> this.reconcile(desired("e1", "Env 1", k8s("a1", Lifetime.ofYears(5)))));
        verify(this.history, never()).persist(any(AssetConfigHistory.class));
    }

    @Test
    void worker_failsOnAnAssetCreatedThroughTheApi()
    {
        AssetEntity user = created(AssetEntity.from("e1", k8s("a1", Lifetime.ofYears(5))));
        when(this.assets.findByIdOptional("a1")).thenReturn(Optional.of(user));

        assertThrows(AssetConflictException.class, () -> this.reconciler().reconcile("east", desired("e1", "Env 1", k8s("a1", Lifetime.ofYears(5)))));
    }

    @Test
    void takesOverSoftDeletedRowsCreatedThroughTheApi()
    {
        EnvironmentEntity environment = created("e1");
        environment.deletedAt = EnvironmentEntity.now();
        AssetEntity asset = created(AssetEntity.from("e1", k8s("a1", Lifetime.ofYears(5))));
        asset.deletedAt = EnvironmentEntity.now();
        when(this.environments.findByIdOptional("e1")).thenReturn(Optional.of(environment));
        when(this.assets.findByIdOptional("a1")).thenReturn(Optional.of(asset));

        this.reconcile(desired("e1", "Env 1", k8s("a1", Lifetime.ofYears(5))));

        assertNull(environment.deletedAt);
        assertTrue(environment.provisioned);
        assertNull(asset.deletedAt);
        assertTrue(asset.provisioned);
    }

    @Test
    void yaml_keepsTheAssetsCreatedThroughTheApi()
    {
        // Same owner as the YAML (null): only the provisioned flag tells them apart.
        AssetEntity user = created(AssetEntity.from("e1", k8s("user", Lifetime.ofYears(5))));
        when(this.assets.findVisible()).thenReturn(List.of(user));

        this.reconcile(desired("e1", "Env 1"));

        assertNull(user.deletedAt);
        verify(this.history, never()).persist(any(AssetConfigHistory.class));
    }

    @Test
    void yaml_keepsTheEnvironmentsCreatedThroughTheApi()
    {
        EnvironmentEntity user = created("user-env");
        when(this.environments.findVisible()).thenReturn(List.of(user));

        this.reconcile(desired("e1", "Env 1"));

        assertNull(user.deletedAt);
    }

    @Test
    void yaml_keepsAnUndeclaredEnvironmentHoldingAssetsCreatedThroughTheApi()
    {
        EnvironmentEntity old = provisioned("old", "Old");
        AssetEntity user = created(AssetEntity.from("old", k8s("user", Lifetime.ofYears(5))));
        when(this.environments.findVisible()).thenReturn(List.of(old));
        when(this.assets.findVisible()).thenReturn(List.of(user));

        this.reconcile(desired("e1", "Env 1"));

        assertNull(old.deletedAt);
    }

    private AssetEntity reconcileAsWorker(String worker, Map<String, Environment> desired)
    {
        this.reconciler().reconcile(worker, desired);
        ArgumentCaptor<AssetEntity> captor = ArgumentCaptor.forClass(AssetEntity.class);
        verify(this.assets, atMost(1)).persist(captor.capture());
        return captor.getAllValues().isEmpty() ? null : captor.getValue();
    }

    /* A reconciler whose YAML declares nothing, so only the worker's declaration is at play. */
    private EnvironmentReconciler reconciler()
    {
        return new EnvironmentReconciler(this.environments, this.assets, new AssetHistoryRecorder(this.history), new MonitoredEnvironments(Map.of()));
    }

    private static EnvironmentEntity provisioned(String id, String name)
    {
        EnvironmentEntity entity = new EnvironmentEntity();
        entity.id = id;
        entity.name = name;
        entity.provisioned = true;
        return entity;
    }

    private static EnvironmentEntity created(String id)
    {
        EnvironmentEntity entity = new EnvironmentEntity();
        entity.id = id;
        entity.name = id;
        return entity;
    }

    private static AssetEntity created(AssetEntity entity)
    {
        entity.provisioned = false;
        return entity;
    }

    private static AssetEntity owned(String owner, AssetEntity entity)
    {
        entity.owner = owner;
        return entity;
    }

    private void reconcile(Map<String, Environment> desired)
    {
        new EnvironmentReconciler(this.environments, this.assets, new AssetHistoryRecorder(this.history), new MonitoredEnvironments(desired))
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

    private static KubernetesAPIAsset k8s(String id, Lifetime lifespan)
    {
        return new KubernetesAPIAsset(
            AssetIdentity.of(id, "e1", id, KubernetesAPIModule.TYPE),
            "/kube/config", "ns", Optional.empty(), Optional.empty(), Period.ZERO,
            new KubernetesAPIServerProfile(ServerProvider.aws, "c5.large", FRANCE, lifespan)
        );
    }
}
