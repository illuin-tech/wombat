package tech.illuin.wombat.engine.simulation.model;

import tech.illuin.wombat.engine.simulation.SimulationPeriod;
import tech.illuin.wombat.engine.simulation.persistence.SimulationEntity;

import java.time.Instant;

/**
 * A simulation as listed, without its assets.
 *
 * @param sourceEnvironmentName {@code null} without a source environment, or once it was deleted
 */
public record SimulationSummary(
    String id,
    String name,
    String description,
    SimulationPeriod referenceWindow,
    String sourceEnvironmentId,
    String sourceEnvironmentName,
    long assetCount,
    Instant createdAt,
    Instant updatedAt
) {
    public static SimulationSummary from(SimulationEntity entity, String sourceEnvironmentName, long assetCount)
    {
        return new SimulationSummary(
            entity.id, entity.name, entity.description, entity.referenceWindow, entity.sourceEnvironmentId, sourceEnvironmentName,
            assetCount, entity.createdAt, entity.updatedAt
        );
    }
}
