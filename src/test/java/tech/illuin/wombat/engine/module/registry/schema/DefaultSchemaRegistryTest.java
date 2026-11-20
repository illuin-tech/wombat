package tech.illuin.wombat.engine.module.registry.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Error;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class DefaultSchemaRegistryTest
{
    private static final SchemaRegistry VALIDATORS = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    @Inject AssetSchemaRegistry schemaRegistry;
    @Inject ModuleRegistry moduleRegistry;
    @Inject ObjectMapper objectMapper;
    @Inject Validator validator;

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

    @Test
    void getSchema_leavesOutIdentityAndFlattensProperties()
    {
        JsonNode schema = schemaRegistry.getSchema(module("llm-anthropic").type()).orElseThrow();

        assertFalse(schema.has("allOf"));
        for (String identity : List.of("id", "environmentId", "type"))
            assertFalse(schema.path("properties").has(identity), identity);
        assertEquals("secret-key", schema.at("/properties/adminKey/format").asText());
        assertEquals("https://api.anthropic.com", schema.at("/properties/apiUrl/default").asText());
        assertTrue(schema.at("/properties/adminKey/description").asText().startsWith("Secret holding"));
        assertTrue(schema.path("required").toString().contains("adminKey"));
    }

    @Test
    void getSchema_listsPropertiesInDeclarationOrder_withProfileLast()
    {
        JsonNode anthropic = schemaRegistry.getSchema(module("llm-anthropic").type()).orElseThrow();
        assertEquals(List.of("apiUrl", "proxyUrl", "adminKey", "profile"), propertyNames(anthropic));
        assertEquals(
            List.of("provider", "instanceType", "location", "lifespan", "nodeCount", "zoneId", "scenarios"),
            propertyNames(schemaRegistry.getSchema(module("kubernetes-static").type()).orElseThrow().at("/properties/profile"))
        );

        for (ModuleMetadata module : moduleRegistry.listModules())
        {
            List<String> names = propertyNames(schemaRegistry.getSchema(module.type()).orElseThrow());
            if (names.contains("profile"))
                assertEquals("profile", names.getLast(), module.type().name());
        }
    }

    private static List<String> propertyNames(JsonNode schema)
    {
        List<String> names = new java.util.ArrayList<>();
        schema.path("properties").fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void getSchema_describesScenariosAsDiscriminatedUnion()
    {
        JsonNode items = schemaRegistry.getSchema(module("kubernetes-static").type()).orElseThrow()
            .at("/properties/profile/properties/scenarios/items");
        JsonNode scenarios = items.path("oneOf");

        assertFalse(items.has("anyOf"));
        assertEquals(
            List.of("constant", "user_activity", "batch_activity"),
            Stream.of(0, 1, 2).map(i -> scenarios.get(i).at("/properties/type/const").asText()).toList()
        );
        assertEquals(
            List.of("Constant", "User activity", "Batch activity"),
            Stream.of(0, 1, 2).map(i -> scenarios.get(i).path("title").asText()).toList()
        );
        assertEquals("user_activity", scenarios.get(1).at("/properties/type/default").asText());
        assertEquals("constant", items.at("/default/type").asText());
        assertEquals("bell", scenarios.get(1).at("/properties/curve/default").asText());
    }

    @Test
    void getSchema_keepsSyntaxOfPatternedStrings()
    {
        JsonNode lifespan = schemaRegistry.getSchema(module("kubernetes-static").type()).orElseThrow()
            .at("/properties/profile/properties/lifespan");

        assertEquals(
            "Expected operational lifespan of the hardware — duration as an amount and a unit suffix: h (hours), d (days), m (months), y (years), e.g. 5y; a bare amount is in hours",
            lifespan.path("description").asText()
        );
    }

    /**
     * Each creatable module has a sample of its properties: the sample must satisfy the schema, be accepted by the
     * asset class, and the asset must serialize back into properties that satisfy the schema.
     */
    @TestFactory
    Stream<DynamicTest> getSchema_matchesPropertiesAcceptedByAssetClass()
    {
        return moduleRegistry.listModules().stream()
            .filter(ModuleMetadata::creatable)
            .map(module -> DynamicTest.dynamicTest(module.type().localName(), () -> {
                var schema = VALIDATORS.getSchema(schemaRegistry.getSchema(module.type()).orElseThrow());
                ObjectNode properties = sample(module);

                List<Error> errors = schema.validate(properties);
                assertTrue(errors.isEmpty(), () -> "Sample rejected by the schema: " + errors);

                ObjectNode withIdentity = properties.deepCopy()
                    .put("id", "sample")
                    .put("environmentId", "sample-env")
                    .put("type", module.type().name());
                Asset asset = objectMapper.treeToValue(withIdentity, module.assetClass());
                assertTrue(validator.validate(asset).isEmpty(), () -> "Sample rejected by the asset class: " + validator.validate(asset));

                /* Optional properties are served as null, which reads the same as an absent property */
                ObjectNode served = withoutNulls(objectMapper.valueToTree(asset));
                served.remove(List.of("id", "environmentId", "type"));
                List<Error> servedErrors = schema.validate(served);
                assertTrue(servedErrors.isEmpty(), () -> "Serialized asset rejected by the schema: " + servedErrors);
            }));
    }

    private static <T extends JsonNode> T withoutNulls(T node)
    {
        if (node instanceof ObjectNode object)
            object.properties().removeIf(entry -> entry.getValue().isNull());
        node.forEach(DefaultSchemaRegistryTest::withoutNulls);
        return node;
    }

    private ModuleMetadata module(String localName)
    {
        return moduleRegistry.listModules().stream()
            .filter(module -> module.type().localName().equals(localName))
            .findFirst()
            .orElseThrow();
    }

    private ObjectNode sample(ModuleMetadata module) throws IOException
    {
        String path = "/asset-properties/" + module.type().localName() + ".json";
        try (InputStream stream = getClass().getResourceAsStream(path))
        {
            assertNotNull(stream, "Missing sample properties " + path);
            return (ObjectNode) objectMapper.readTree(stream);
        }
    }
}
