package tech.illuin.wombat.engine.context.model;

import tech.illuin.wombat.engine.context.persistence.AssetEntity;
import tech.illuin.wombat.engine.context.persistence.EnvironmentEntity;

import java.time.Instant;
import java.util.List;

public record EnvironmentInfo(
    String id,
    String uuid,
    String name,
    Instant createdAt,
    Instant updatedAt,
    Instant disabledAt,
    List<AssetInfo> assets
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
