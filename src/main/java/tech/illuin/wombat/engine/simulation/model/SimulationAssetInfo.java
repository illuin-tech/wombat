package tech.illuin.wombat.engine.simulation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.engine.simulation.persistence.SimulationAssetEntity;

import java.time.Instant;

/**
 * @param parentStatus {@code null} without a parent
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimulationAssetInfo(
    @JsonUnwrapped Asset properties,
    String simulationId,
    String name,
    String description,
    String notes,
    String parentAssetId,
    String parentEnvironmentId,
    ParentStatus parentStatus,
    Instant syncedAt,
    Instant createdAt,
    Instant updatedAt
) {
    public static SimulationAssetInfo from(SimulationAssetEntity entity, ParentStatus parentStatus)
    {
        return new SimulationAssetInfo(
            entity.properties,
            entity.simulationId,
            entity.name,
            entity.description,
            entity.notes,
            entity.parentAssetId,
            entity.parentEnvironmentId,
            parentStatus,
            entity.syncedAt,
            entity.createdAt,
            entity.updatedAt
        );
    }
}
