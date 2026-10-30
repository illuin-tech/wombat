package tech.illuin.wombat.engine.context.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.illuin.wombat.engine.context.persistence.AssetEntity;
import tech.illuin.wombat.engine.context.persistence.EnvironmentEntity;

import java.time.Instant;
import java.util.List;

public record EnvironmentInfo(
    @JsonProperty("id") String id,
    @JsonProperty("uuid") String uuid,
    @JsonProperty("name") String name,
    @JsonProperty("createdAt") @JsonAlias("created-at") Instant createdAt,
    @JsonProperty("updatedAt") @JsonAlias("updated-at") Instant updatedAt,
    @JsonProperty("disabledAt") @JsonAlias("disabled-at") Instant disabledAt,
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
