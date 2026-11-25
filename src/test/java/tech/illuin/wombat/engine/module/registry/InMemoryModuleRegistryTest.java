package tech.illuin.wombat.engine.module.registry;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.module.boavizta.BoaviztaModule;

import java.util.List;
import java.util.Set;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        ModuleMetadata expected = ModuleMetadata.core(module.type(), module.assetClass().orElse(null), false);
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
}
