package tech.illuin.wombat.engine.simulation.model;

import tech.illuin.wombat.engine.simulation.SimulationPeriod;
import tech.illuin.wombat.engine.simulation.persistence.SimulationEntity;

import java.time.Instant;
import java.util.List;

/**
 * @param parentEnvironmentName {@code null} without a parent environment, or once it was deleted
 */
public record SimulationInfo(
    String id,
    String name,
    String description,
    String notes,
    SimulationPeriod referenceWindow,
    String parentEnvironmentId,
    String parentEnvironmentName,
    Instant createdAt,
    Instant updatedAt,
    List<SimulationAssetInfo> assets
) {
    public static SimulationInfo from(SimulationEntity entity, String parentEnvironmentName, List<SimulationAssetInfo> assets)
    {
        return new SimulationInfo(
            entity.id, entity.name, entity.description, entity.notes, entity.referenceWindow, entity.parentEnvironmentId,
            parentEnvironmentName, entity.createdAt, entity.updatedAt, assets
        );
    }
}
