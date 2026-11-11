package tech.illuin.wombat.engine.module.registry.schema;

import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.victools.jsonschema.generator.OptionPreset.PLAIN_JSON;
import static com.github.victools.jsonschema.generator.SchemaVersion.DRAFT_2020_12;

public class DefaultSchemaRegistry implements AssetSchemaRegistry
{
    private final ModuleRegistry moduleRegistry;
    private final SchemaGenerator schemaGenerator;
    private final Map<AssetType, JsonNode> cache;

    public DefaultSchemaRegistry(ModuleRegistry moduleRegistry)
    {
        this.moduleRegistry = moduleRegistry;
        this.schemaGenerator = createGenerator();
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<JsonNode> getSchema(AssetType type)
    {
        JsonNode node = this.cache.computeIfAbsent(type, k -> this.moduleRegistry.getModule(k)
            .map(mm -> this.schemaGenerator.generateSchema(mm.assetClass()))
            .orElse(null)
        );
        return Optional.ofNullable(node);
    }

    private static SchemaGenerator createGenerator()
    {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(DRAFT_2020_12, PLAIN_JSON)
            .with(new JacksonSchemaModule(
                JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
                JacksonOption.RESPECT_JSONPROPERTY_ORDER
            ));

        return new SchemaGenerator(configBuilder.build());
    }
}
