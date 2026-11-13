package tech.illuin.wombat.engine.context.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import tech.illuin.wombat.engine.context.persistence.AssetEntity;
import tech.illuin.wombat.core.asset.Asset;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssetInfo(
    @JsonUnwrapped Asset properties,
    String type,
    String uuid,
    String owner,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt
) {
    public AssetInfo(Asset properties, String uuid, Instant createdAt, Instant updatedAt, Instant deletedAt)
    {
        this(
            properties,
            properties.identity().assetType(),
            uuid,
            null,
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
            entity.owner,
            entity.createdAt,
            entity.updatedAt,
            entity.deletedAt
        );
    }
}
