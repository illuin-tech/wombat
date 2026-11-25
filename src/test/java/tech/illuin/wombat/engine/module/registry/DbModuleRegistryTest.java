package tech.illuin.wombat.engine.module.registry;

import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.FamilyType;
import tech.illuin.wombat.core.asset.type.ModuleType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.engine.module.persistence.CoreInstanceEntity;
import tech.illuin.wombat.engine.module.persistence.CoreInstanceRepository;
import tech.illuin.wombat.engine.module.persistence.ModuleEntity;
import tech.illuin.wombat.engine.module.persistence.ModuleRepository;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata.Feature;
import tech.illuin.wombat.module.boavizta.BoaviztaModule;
import tech.illuin.wombat.module.ecologits.EcologitsModule;
import tech.illuin.wombat.module.kubernetes_metrics.KubernetesMetricsModule;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DbModuleRegistryTest
{
    private static final String K8S = KubernetesSimulatedModule.TYPE.name();
    private static final String LLM = LLMSimulatedModule.TYPE.name();
    private static final AssetType REMOVED = AssetType.of("acme", "removed", ActivityRegime.MEASURED, ServiceFamily.LLM);
    private static final String METRICS = KubernetesMetricsModule.TYPE.name();
    private static final String BOAVIZTA = BoaviztaModule.TYPE.name();
    private static final FamilyType REMOVED_ESTIMATOR = FamilyType.of("acme", "removed-estimator", ServiceFamily.KUBERNETES_CONTAINER);

    private ModuleRepository repository;
    private CoreInstanceRepository instances;
    private DbModuleRegistry registry;

    @BeforeEach
    void setUp()
    {
        this.repository = mock(ModuleRepository.class);
        this.instances = mock(CoreInstanceRepository.class);
        this.registry = new DbModuleRegistry(
            List.of(new KubernetesSimulatedModule(), new LLMSimulatedModule()),
            this.repository,
            this.instances,
            Path.of("extensions"),
            Duration.ofSeconds(30)
        );
        when(this.repository.findByIdOptional(any())).thenReturn(Optional.empty());
        when(this.instances.findLive(any())).thenReturn(List.of());
    }

    @Test
    void startupRegistersEveryDetectedModule()
    {
        when(this.repository.register(any())).thenReturn(Set.of());

        this.registry.onStart(null);

        verify(this.repository).register(argThat(types -> Set.copyOf(types).equals(Set.of(KubernetesSimulatedModule.TYPE, LLMSimulatedModule.TYPE))));
    }

    @Test
    void theDetectedModulesAreRegisteredAndListedInDetectionOrder()
    {
        List<WombatModule> detected = List.of(new LLMSimulatedModule(), new BoaviztaModule(), new KubernetesSimulatedModule());
        DbModuleRegistry registry = new DbModuleRegistry(detected, this.repository, this.instances, Path.of("extensions"), Duration.ofSeconds(30));
        when(this.repository.register(any())).thenReturn(Set.of());
        when(this.repository.listAll()).thenReturn(List.of());

        registry.onStart(null);

        List<ModuleType> types = detected.stream().map(WombatModule::type).toList();
        verify(this.repository).register(types);
        assertEquals(types, registry.listModules().stream().map(ModuleMetadata::type).toList());
    }

    @Test
    void aFailedRegistrationDoesNotFailTheStartup()
    {
        when(this.repository.register(any())).thenThrow(new PersistenceException("unique constraint violated"));

        assertDoesNotThrow(() -> this.registry.onStart(null));
        assertEquals(Set.of(), this.registry.newlyRegistered());
    }

    @Test
    void startupTellsTheModulesItRegisteredForTheFirstTime()
    {
        when(this.repository.register(any())).thenReturn(Set.of(LLM));

        this.registry.onStart(null);

        assertEquals(Set.of(LLM), this.registry.newlyRegistered());
    }

    @Test
    void aFamilyModuleIsDetected_butOffersNoAssetType()
    {
        DbModuleRegistry registry = new DbModuleRegistry(List.of(new BoaviztaModule()), this.repository, this.instances, Path.of("extensions"), Duration.ofSeconds(30));

        ModuleMetadata boavizta = registry.getModule(BoaviztaModule.TYPE).orElseThrow();

        assertEquals(ModuleMetadata.Kind.family, boavizta.kind());
        assertTrue(boavizta.active());
        assertNull(boavizta.assetClass());
        assertFalse(boavizta.creatable());
    }

    @Test
    void aFamilyModuleRowKeepsItsType_withoutRegime()
    {
        ModuleEntity entity = ModuleEntity.registered(BoaviztaModule.TYPE);

        assertEquals(ModuleMetadata.Kind.family, entity.kind);
        assertNull(entity.regime);
        assertEquals(BoaviztaModule.TYPE, entity.type());
        assertEquals(KubernetesSimulatedModule.TYPE, ModuleEntity.registered(KubernetesSimulatedModule.TYPE).type());
    }

    @Test
    void detectedModulesAreEnabledAsTheirRowSays_orEnabledWithoutRow()
    {
        when(this.repository.listAll()).thenReturn(List.of(row(KubernetesSimulatedModule.TYPE, false)));

        Map<String, Boolean> enabled = this.registry.listModules().stream()
            .collect(Collectors.toMap(module -> module.type().name(), ModuleMetadata::enabled));

        assertEquals(Map.of(K8S, false, LLM, true), enabled);
    }

    @Test
    void aRegisteredModuleNoLongerDetectedIsListedMissing_withItsAdvertisedType()
    {
        when(this.repository.listAll()).thenReturn(List.of(row(REMOVED, false)));

        List<ModuleMetadata> modules = this.registry.listModules();

        assertEquals(3, modules.size());
        ModuleMetadata missing = modules.getLast();
        assertEquals(REMOVED, missing.type());
        assertTrue(missing.missing());
        assertFalse(missing.enabled());
        assertFalse(missing.active());
        assertFalse(missing.creatable());
        assertNull(missing.assetClass());
        assertNull(missing.source());
    }

    @Test
    void aModuleDetectedByAnotherLiveInstanceOnly_isNotMissing_butNotActiveHere()
    {
        when(this.repository.listAll()).thenReturn(List.of(row(REMOVED, true)));
        when(this.instances.findLive(any())).thenReturn(List.of(instance(REMOVED.name())));

        ModuleMetadata elsewhere = this.registry.listModules().getLast();

        assertFalse(elsewhere.missing());
        assertFalse(elsewhere.detectedHere());
        assertFalse(elsewhere.active());
        assertFalse(elsewhere.creatable());
    }

    @Test
    void aModuleDetectedByAnotherLiveInstance_canBeToggled_butNotRemoved()
    {
        ModuleEntity row = row(REMOVED, true);
        when(this.repository.findById(REMOVED.name())).thenReturn(row);
        when(this.repository.findByIdOptional(REMOVED.name())).thenReturn(Optional.of(row));
        when(this.instances.findLive(any())).thenReturn(List.of(instance(REMOVED.name())));

        assertFalse(this.registry.setEnabled(REMOVED.name(), false, null).enabled());
        assertThrows(ModuleConflictException.class, () -> this.registry.remove(REMOVED.name()));
    }

    @Test
    void getModule_returnsDetectedAndMissingModules()
    {
        when(this.repository.findById(K8S)).thenReturn(row(KubernetesSimulatedModule.TYPE, false));
        when(this.repository.findById(REMOVED.name())).thenReturn(row(REMOVED, true));

        assertFalse(this.registry.getModule(K8S).orElseThrow().enabled());
        assertTrue(this.registry.getModule(LLM).orElseThrow().active());
        assertTrue(this.registry.getModule(REMOVED.name()).orElseThrow().missing());
        assertTrue(this.registry.getModule("acme.unknown").isEmpty());
    }

    @Test
    void aModuleFromTheClasspathIsACoreOne()
    {
        ModuleMetadata module = this.registry.getModule(K8S).orElseThrow();

        assertEquals(ModuleMetadata.Source.core, module.source());
    }

    @Test
    void setEnabled_updatesTheRowAndWhoChangedIt()
    {
        ModuleEntity row = row(KubernetesSimulatedModule.TYPE, true);
        when(this.repository.findById(K8S)).thenReturn(row);

        ModuleMetadata module = this.registry.setEnabled(K8S, false, "alice");

        assertFalse(module.enabled());
        assertFalse(row.enabled);
        assertEquals("alice", row.updatedBy);
    }

    @Test
    void setEnabled_registersADetectedModuleWithoutRow()
    {
        this.registry.setEnabled(K8S, false, null);

        verify(this.repository).persist(any(ModuleEntity.class));
    }

    @Test
    void setEnabled_onAMissingModuleIsAConflict()
    {
        when(this.repository.findById(REMOVED.name())).thenReturn(row(REMOVED, true));

        assertThrows(ModuleConflictException.class, () -> this.registry.setEnabled(REMOVED.name(), false, null));
    }

    @Test
    void setEnabled_onAnUnknownModuleIsNotFound()
    {
        assertThrows(UnknownModuleException.class, () -> this.registry.setEnabled("acme.unknown", false, null));
    }

    @Test
    void remove_deletesTheRowOfAMissingModule()
    {
        ModuleEntity row = row(REMOVED, true);
        when(this.repository.findByIdOptional(REMOVED.name())).thenReturn(Optional.of(row));

        this.registry.remove(REMOVED.name());

        verify(this.repository).delete(row);
    }

    @Test
    void remove_onADetectedModuleIsAConflict()
    {
        when(this.repository.findByIdOptional(K8S)).thenReturn(Optional.of(row(KubernetesSimulatedModule.TYPE, true)));

        assertThrows(ModuleConflictException.class, () -> this.registry.remove(K8S));
        verify(this.repository, never()).delete(any());
    }

    @Test
    void listModules_tellsTheOrderingOfTheFamilyModules_andWhatEachModuleProvides()
    {
        when(this.repository.listAll()).thenReturn(List.of(ordered(KubernetesMetricsModule.TYPE, 1), ordered(BoaviztaModule.TYPE, 2), ordered(REMOVED_ESTIMATOR, 3)));

        Map<String, ModuleMetadata> modules = this.kubernetesFamily().listModules().stream()
            .collect(Collectors.toMap(module -> module.type().name(), Function.identity()));

        assertEquals(2, modules.get(BOAVIZTA).ordering());
        assertEquals(Set.of(Feature.impact), modules.get(BOAVIZTA).provides());
        assertEquals(Set.of(Feature.activity), modules.get(METRICS).provides());
        assertNull(modules.get(K8S).ordering());
        assertEquals(3, modules.get(REMOVED_ESTIMATOR.name()).ordering());
        assertEquals(Set.of(), modules.get(REMOVED_ESTIMATOR.name()).provides(), "not detected here, so unknown");
    }

    @Test
    void setFamilyOrder_ordersTheFamilyModulesAsGiven_andTellsWhoReorderedThem()
    {
        ModuleEntity metrics = ordered(KubernetesMetricsModule.TYPE, 1);
        ModuleEntity boavizta = ordered(BoaviztaModule.TYPE, 2);
        this.rows(metrics, boavizta);

        this.kubernetesFamily().setFamilyOrder(ServiceFamily.KUBERNETES_CONTAINER, List.of(BOAVIZTA, METRICS), "alice");

        assertEquals(1, boavizta.ordering);
        assertEquals(2, metrics.ordering);
        assertEquals("alice", boavizta.updatedBy);
        assertEquals("alice", metrics.updatedBy);
    }

    @Test
    void setFamilyOrder_leavesTheModulesWhichKeepTheirOrdering()
    {
        ModuleEntity metrics = ordered(KubernetesMetricsModule.TYPE, 1);
        this.rows(metrics, ordered(BoaviztaModule.TYPE, 2));

        this.kubernetesFamily().setFamilyOrder(ServiceFamily.KUBERNETES_CONTAINER, List.of(METRICS, BOAVIZTA), "alice");

        assertEquals(1, metrics.ordering);
        assertNull(metrics.updatedBy);
    }

    @Test
    void setFamilyOrder_registersADetectedModuleWithoutRow()
    {
        this.rows(ordered(BoaviztaModule.TYPE, 1));

        this.kubernetesFamily().setFamilyOrder(ServiceFamily.KUBERNETES_CONTAINER, List.of(BOAVIZTA, METRICS), "alice");

        verify(this.repository).persist(argThat((ModuleEntity entity) -> entity.typeName.equals(METRICS) && entity.ordering == 2));
    }

    @Test
    void setFamilyOrder_mustListEachFamilyModuleOfTheFamilyOnce_missingOnesIncluded()
    {
        this.rows(ordered(KubernetesMetricsModule.TYPE, 1), ordered(BoaviztaModule.TYPE, 2), ordered(REMOVED_ESTIMATOR, 3));
        DbModuleRegistry registry = this.kubernetesFamily();
        ServiceFamily family = ServiceFamily.KUBERNETES_CONTAINER;
        String removed = REMOVED_ESTIMATOR.name();

        assertThrows(ModuleOrderException.class, () -> registry.setFamilyOrder(family, List.of(BOAVIZTA, METRICS), "alice"));
        assertThrows(ModuleOrderException.class, () -> registry.setFamilyOrder(family, List.of(BOAVIZTA, METRICS, METRICS), "alice"));
        assertThrows(ModuleOrderException.class, () -> registry.setFamilyOrder(family, List.of(BOAVIZTA, METRICS, removed, K8S), "alice"));
        assertThrows(ModuleOrderException.class, () -> registry.setFamilyOrder(family, List.of(BOAVIZTA, METRICS, removed, EcologitsModule.TYPE.name()), "alice"));
        assertDoesNotThrow(() -> registry.setFamilyOrder(family, List.of(removed, BOAVIZTA, METRICS), "alice"));
    }

    @Test
    void remove_onAnUnknownModuleIsNotFound()
    {
        assertThrows(UnknownModuleException.class, () -> this.registry.remove("acme.unknown"));
    }

    private static CoreInstanceEntity instance(String... detected)
    {
        CoreInstanceEntity instance = new CoreInstanceEntity();
        instance.instanceId = "other";
        instance.detectedModules = Set.of(detected);
        return instance;
    }

    private static ModuleEntity row(AssetType type, boolean enabled)
    {
        ModuleEntity entity = ModuleEntity.registered(type);
        entity.enabled = enabled;
        return entity;
    }

    private static ModuleEntity ordered(FamilyType type, int ordering)
    {
        ModuleEntity entity = ModuleEntity.registered(type);
        entity.ordering = ordering;
        return entity;
    }

    /* Listed, and found by name */
    private void rows(ModuleEntity... rows)
    {
        when(this.repository.listAll()).thenReturn(List.of(rows));
        for (ModuleEntity row : rows)
            when(this.repository.findById(row.typeName)).thenReturn(row);
    }

    /* Detects an asset-type of the Kubernetes family, and the two family modules Wombat ships for it */
    private DbModuleRegistry kubernetesFamily()
    {
        return new DbModuleRegistry(
            List.of(new KubernetesSimulatedModule(), new KubernetesMetricsModule(), new BoaviztaModule()),
            this.repository,
            this.instances,
            Path.of("extensions"),
            Duration.ofSeconds(30)
        );
    }
}
