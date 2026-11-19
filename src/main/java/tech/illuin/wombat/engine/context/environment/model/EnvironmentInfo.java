package tech.illuin.wombat.engine.context.environment.model;


import tech.illuin.wombat.engine.context.asset.model.AssetInfo;
import tech.illuin.wombat.engine.context.asset.persistence.AssetEntity;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentEntity;
import java.time.Instant;
import java.util.List;

public record EnvironmentInfo(
    String id,
    String uuid,
    String name,
    String description,
    boolean provisioned,
    Instant createdAt,
    Instant updatedAt,
    Instant disabledAt,
    List<AssetInfo> assets
) {
    public static EnvironmentInfo from(EnvironmentEntity entity, List<AssetEntity> assets)
    {
        return new EnvironmentInfo(
            entity.id, entity.uuid, entity.name, entity.description, entity.provisioned,
            entity.createdAt, entity.updatedAt, entity.disabledAt,
            assets.stream().map(AssetInfo::from).toList()
        );
    }
}
