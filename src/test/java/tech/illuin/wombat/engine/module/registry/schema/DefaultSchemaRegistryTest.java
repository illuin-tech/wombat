package tech.illuin.wombat.engine.module.registry.schema;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.engine.module.registry.InMemoryModuleRegistry;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSchemaRegistryTest
{
    @Test
    void getSchema_generatesAndCachesSchemaForRegisteredModule()
    {
        KubernetesSimulatedModule module = new KubernetesSimulatedModule();
        InMemoryModuleRegistry moduleRegistry = new InMemoryModuleRegistry();
        moduleRegistry.registerModule(module, true);
        DefaultSchemaRegistry schemaRegistry = new DefaultSchemaRegistry(moduleRegistry);

        JsonNode schema = schemaRegistry.getSchema(module.type()).orElseThrow();

        assertTrue(schema.has("$schema"));
        assertTrue(schema.path("properties").has("activity"));
        assertSame(schema, schemaRegistry.getSchema(module.type()).orElseThrow());
    }

    @Test
    void getSchema_returnsEmptyForUnregisteredType()
    {
        DefaultSchemaRegistry schemaRegistry = new DefaultSchemaRegistry(new InMemoryModuleRegistry());

        assertTrue(schemaRegistry.getSchema(new LLMSimulatedModule().type()).isEmpty());
    }
}