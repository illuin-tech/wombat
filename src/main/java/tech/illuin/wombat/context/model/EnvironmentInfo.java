package tech.illuin.wombat.context.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import tech.illuin.wombat.context.persistence.AssetEntity;
import tech.illuin.wombat.context.persistence.EnvironmentEntity;

import java.time.Instant;
import java.util.List;

public record EnvironmentInfo(
    @JsonProperty("id") String id,
    @JsonProperty("uuid") String uuid,
    @JsonProperty("name") String name,
    @JsonProperty("created-at") Instant createdAt,
    @JsonProperty("updated-at") Instant updatedAt,
    @JsonProperty("disabled-at") Instant disabledAt,
    @JsonProperty("assets") List<AssetInfo> assets
) {
    public static EnvironmentInfo from(EnvironmentEntity entity, List<AssetEntity> assets)
    {
        return new EnvironmentInfo(
            entity.id, entity.uuid, entity.name,
            entity.createdAt, entity.updatedAt, entity.disabledAt,
            assets.stream().map(AssetInfo::from).toList()
        );
    }
}
