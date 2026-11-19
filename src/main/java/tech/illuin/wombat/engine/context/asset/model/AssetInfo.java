package tech.illuin.wombat.engine.context.asset.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.engine.context.asset.persistence.AssetEntity;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssetInfo(
    @JsonUnwrapped Asset properties,
    String name,
    String type,
    String uuid,
    String owner,
    String description,
    boolean provisioned,
    Instant createdAt,
    Instant updatedAt,
    Instant disabledAt,
    Instant deletedAt
) {
    public AssetInfo(Asset properties, String uuid, Instant createdAt, Instant updatedAt, Instant deletedAt)
    {
        this(
            properties,
            properties.identity().id(),
            properties.identity().assetType(),
            uuid,
            null,
            null,
            false,
            createdAt,
            updatedAt,
            null,
            deletedAt
        );
    }

    public static AssetInfo from(AssetEntity entity)
    {
        return new AssetInfo(
            entity.properties,
            entity.name,
            entity.type,
            entity.uuid,
            entity.owner,
            entity.description,
            entity.provisioned,
            entity.createdAt,
            entity.updatedAt,
            entity.disabledAt,
            entity.deletedAt
        );
    }
}
