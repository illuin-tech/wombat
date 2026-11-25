package tech.illuin.wombat.engine.module.registry;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.type.ModuleType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.module.boavizta.BoaviztaModule;
import tech.illuin.wombat.module.ecologits.EcologitsModule;
import tech.illuin.wombat.module.kubernetes_metrics.KubernetesMetricsModule;

import java.util.List;
import java.util.Set;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryModuleRegistryTest
{
    @Test
    void registerModule_listsAndFindsModuleMetadata()
    {
        KubernetesSimulatedModule module = new KubernetesSimulatedModule();
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();

        registry.registerModule(module, false);

        ModuleMetadata expected = ModuleMetadata.core(module, false, null);
        assertEquals(Set.of(expected), Set.copyOf(registry.listModules()));
        assertEquals(expected, registry.getModule(module.type().name()).orElseThrow());
        assertEquals(expected, registry.getModule(module.type()).orElseThrow());
        assertTrue(registry.getModule("missing-module").isEmpty());
    }

    @Test
    void registerModule_replacesMetadataForAnExistingId()
    {
        KubernetesSimulatedModule module = new KubernetesSimulatedModule();
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();

        registry.registerModule(module, false);
        registry.registerModule(module, true);

        assertTrue(registry.getModule(module.type().name()).orElseThrow().enabled());
        assertEquals(1, registry.listModules().size());
    }

    @Test
    void listModules_keepsTheRegistrationOrder()
    {
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();
        registry.registerModule(new LLMSimulatedModule(), true);
        registry.registerModule(new BoaviztaModule(), true);
        registry.registerModule(new KubernetesSimulatedModule(), true);

        assertEquals(
            List.of(LLMSimulatedModule.TYPE, BoaviztaModule.TYPE, KubernetesSimulatedModule.TYPE),
            registry.listModules().stream().map(ModuleMetadata::type).toList()
        );
    }

    @Test
    void aFamilyModuleIsPlacedLastInItsFamily_andKeepsItsOrderingWhenRegisteredAgain()
    {
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();
        registry.registerModule(new KubernetesSimulatedModule(), true);
        registry.registerModule(new KubernetesMetricsModule(), true);
        registry.registerModule(new EcologitsModule(), true);
        registry.registerModule(new BoaviztaModule(), true);
        registry.registerModule(new KubernetesMetricsModule(), false);

        assertEquals(1, ordering(registry, KubernetesMetricsModule.TYPE));
        assertEquals(2, ordering(registry, BoaviztaModule.TYPE));
        assertEquals(1, ordering(registry, EcologitsModule.TYPE));
        assertNull(ordering(registry, KubernetesSimulatedModule.TYPE));
    }

    @Test
    void setFamilyOrder_ordersTheFamilyModulesAsGiven()
    {
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();
        registry.registerModule(new KubernetesMetricsModule(), true);
        registry.registerModule(new BoaviztaModule(), false);

        registry.setFamilyOrder(ServiceFamily.KUBERNETES_CONTAINER, List.of(BoaviztaModule.TYPE.name(), KubernetesMetricsModule.TYPE.name()), null);

        assertEquals(1, ordering(registry, BoaviztaModule.TYPE));
        assertEquals(2, ordering(registry, KubernetesMetricsModule.TYPE));
        assertFalse(registry.getModule(BoaviztaModule.TYPE).orElseThrow().enabled());
    }

    @Test
    void setFamilyOrder_mustListEachFamilyModuleOfTheFamilyOnce()
    {
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();
        registry.registerModule(new KubernetesSimulatedModule(), true);
        registry.registerModule(new KubernetesMetricsModule(), true);
        registry.registerModule(new BoaviztaModule(), true);
        ServiceFamily family = ServiceFamily.KUBERNETES_CONTAINER;
        String metrics = KubernetesMetricsModule.TYPE.name();
        String boavizta = BoaviztaModule.TYPE.name();

        assertThrows(ModuleOrderException.class, () -> registry.setFamilyOrder(family, List.of(boavizta), null));
        assertThrows(ModuleOrderException.class, () -> registry.setFamilyOrder(family, List.of(boavizta, boavizta), null));
        assertThrows(ModuleOrderException.class, () -> registry.setFamilyOrder(family, List.of(boavizta, metrics, KubernetesSimulatedModule.TYPE.name()), null));
        assertEquals(1, ordering(registry, KubernetesMetricsModule.TYPE), "a rejected order changes nothing");
    }

    @Test
    void unregisteredTypeIsAbsent()
    {
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();

        assertTrue(registry.getModule(new LLMSimulatedModule().type().name()).isEmpty());
        assertTrue(registry.listModules().isEmpty());
    }

    @Test
    void setEnabled_togglesARegisteredModule()
    {
        KubernetesSimulatedModule module = new KubernetesSimulatedModule();
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();
        registry.registerModule(module, true);

        assertFalse(registry.setEnabled(module.type().name(), false, null).enabled());
        assertFalse(registry.getModule(module.type()).orElseThrow().enabled());
        assertThrows(UnknownModuleException.class, () -> registry.setEnabled("unknown", false, null));
    }

    @Test
    void remove_refusesEveryModule_sinceNoneIsMissing()
    {
        KubernetesSimulatedModule module = new KubernetesSimulatedModule();
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry();
        registry.registerModule(module, true);

        assertThrows(ModuleConflictException.class, () -> registry.remove(module.type().name()));
        assertThrows(UnknownModuleException.class, () -> registry.remove("unknown"));
    }

    private static Integer ordering(InMemoryModuleRegistry registry, ModuleType type)
    {
        return registry.getModule(type).orElseThrow().ordering();
    }
}
