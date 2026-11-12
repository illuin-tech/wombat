package tech.illuin.wombat.engine.module.registry.schema;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class DefaultSchemaRegistryTest
{
    @Inject AssetSchemaRegistry schemaRegistry;
    @Inject ModuleRegistry moduleRegistry;

    @Test
    void getSchema_generatesAndCachesSchemaForRegisteredModule()
    {
        KubernetesSimulatedModule module = new KubernetesSimulatedModule();
        moduleRegistry.getModule(module.type().name()).orElseThrow();

        JsonNode schema = schemaRegistry.getSchema(module.type()).orElseThrow();

        assertTrue(schema.has("$schema"));
        assertTrue(schema.path("properties").has("activity"));
        assertSame(schema, schemaRegistry.getSchema(module.type()).orElseThrow());
    }

    @Test
    void getSchema_returnsEmptyForUnregisteredType()
    {
        AssetType unregisteredType = AssetType.of("tech.illuin", "wombat-module", "nonexistent",
            tech.illuin.wombat.core.asset.type.ActivityRegime.MODELED, ServiceFamily.KUBERNETES_CONTAINER);
        assertTrue(schemaRegistry.getSchema(unregisteredType).isEmpty());
    }
}