package tech.illuin.wombat.engine.module.registry;

import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.engine.module.persistence.ModuleEntity;
import tech.illuin.wombat.engine.module.persistence.ModuleRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    private ModuleRepository repository;
    private DbModuleRegistry registry;

    @BeforeEach
    void setUp()
    {
        this.repository = mock(ModuleRepository.class);
        this.registry = new DbModuleRegistry(List.of(new KubernetesSimulatedModule(), new LLMSimulatedModule()), this.repository, Path.of("extensions"));
        when(this.repository.findByIdOptional(any())).thenReturn(Optional.empty());
    }

    @Test
    void startupRegistersEveryDetectedModule()
    {
        when(this.repository.register(any())).thenReturn(Set.of());

        this.registry.onStart(null);

        verify(this.repository).register(argThat(types -> Set.copyOf(types).equals(Set.of(KubernetesSimulatedModule.TYPE, LLMSimulatedModule.TYPE))));
    }

    @Test
    void aFailedRegistrationDoesNotFailTheStartup()
    {
        when(this.repository.register(any())).thenThrow(new PersistenceException("unique constraint violated"));

        assertDoesNotThrow(() -> this.registry.onStart(null));
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
    void remove_onAnUnknownModuleIsNotFound()
    {
        assertThrows(UnknownModuleException.class, () -> this.registry.remove("acme.unknown"));
    }

    private static ModuleEntity row(AssetType type, boolean enabled)
    {
        ModuleEntity entity = ModuleEntity.registered(type);
        entity.enabled = enabled;
        return entity;
    }
}
