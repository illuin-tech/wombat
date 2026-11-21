package tech.illuin.wombat.engine.simulation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentEntity;
import tech.illuin.wombat.engine.simulation.SimulationPeriod;

import java.time.Instant;

@Entity
@Table(name = "simulations")
public class SimulationEntity
{
    @Id @Column(nullable = false)
    public String id;

    @Column(nullable = false)
    public String name;

    @Column
    public String description;

    /** Long-form Markdown, e.g. the assumptions the simulation rests on. */
    @Column
    public String notes;

    /** The window evaluated, then scaled to every period. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reference_window", nullable = false, columnDefinition = "TEXT")
    public SimulationPeriod referenceWindow;

    /** The environment the simulation was generated from, if any; it may have been deleted since. */
    @Column(name = "source_environment_id")
    public String sourceEnvironmentId;

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
}
