package tech.illuin.wombat.context.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import tech.illuin.wombat.context.persistence.AssetEntity;
import tech.illuin.wombat.core.asset.Asset;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssetInfo(
    @JsonUnwrapped Asset properties,
    @JsonProperty("type") String type,
    @JsonProperty("uuid") String uuid,
    @JsonProperty("created-at") Instant createdAt,
    @JsonProperty("updated-at") Instant updatedAt,
    @JsonProperty("deleted-at") Instant deletedAt
) {
    public AssetInfo(Asset properties, String uuid, Instant createdAt, Instant updatedAt, Instant deletedAt)
    {
        this(
            properties,
            properties instanceof UnrecognizedAsset unrecognized ? unrecognized.rawType() : properties.type().name(),
            uuid,
            createdAt,
            updatedAt,
            deletedAt
        );
    }

    public static AssetInfo from(AssetEntity entity)
    {
        return new AssetInfo(
            entity.properties,
            entity.type,
            entity.uuid,
            entity.createdAt,
            entity.updatedAt,
            entity.deletedAt
        );
    }
}
