package tech.illuin.wombat.engine.simulation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConverter;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentEntity;

import java.io.Serializable;
import java.time.Instant;

/**
 * An asset definition held by a simulation: a copy of a real asset, or one written from scratch. Editing its
 * {@code properties} is what a simulation is about; nothing here is ever monitored.
 */
@Entity
@Table(name = "simulation_assets")
@IdClass(SimulationAssetEntity.Key.class)
public class SimulationAssetEntity
{
    @Id @Column(name = "simulation_id", nullable = false)
    public String simulationId;

    /** Unique within its simulation only. */
    @Id @Column(nullable = false)
    public String id;

    @Column(nullable = false)
    public String name;

    @Column
    public String description;

    @Column
    public String notes;

    /** The asset the definition was copied from, in any environment; it may have been deleted since. */
    @Column(name = "parent_asset_id")
    public String parentAssetId;

    /** The parent's environment, as it was at copy time. */
    @Column(name = "parent_environment_id")
    public String parentEnvironmentId;

    @Column(nullable = false)
    public String type;

    /** The definition, under the identity of the simulation asset. */
    @Convert(converter = AssetConverter.class)
    @Column(name = "properties", nullable = false)
    public Asset properties;

    /** When the definition was last taken from the parent; {@code null} without a parent. */
    @Column(name = "synced_at", columnDefinition = "INTEGER")
    public Instant syncedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "INTEGER")
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "INTEGER")
    public Instant updatedAt;

    @PrePersist
    void onPersist()
    {
        this.createdAt = EnvironmentEntity.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate()
    {
        this.updatedAt = EnvironmentEntity.now();
    }

    public record Key(String simulationId, String id) implements Serializable {}
}
