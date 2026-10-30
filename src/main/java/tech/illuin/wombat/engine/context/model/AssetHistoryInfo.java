package tech.illuin.wombat.engine.context.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import tech.illuin.wombat.engine.context.persistence.AssetConfigAction;
import tech.illuin.wombat.engine.context.persistence.AssetConfigHistory;

import java.time.Instant;

public record AssetHistoryInfo(
    @JsonProperty("action") AssetConfigAction action,
    @JsonProperty("changedAt") @JsonAlias("changed_at") Instant changedAt,
    @JsonProperty("snapshot") JsonNode snapshot
)
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    public static AssetHistoryInfo from(AssetConfigHistory entity)
    {
        try
        {
            return new AssetHistoryInfo(entity.action, entity.changedAt, MAPPER.readTree(entity.snapshot));
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse asset config snapshot for " + entity.assetId, e);
        }
    }
}
