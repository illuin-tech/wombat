package tech.illuin.wombat.engine.module.registry.schema;

import tech.illuin.wombat.core.asset.type.AssetType;
import tools.jackson.databind.JsonNode;

import java.util.Optional;

public interface AssetSchemaRegistry
{
    Optional<JsonNode> getSchema(AssetType type);
}
