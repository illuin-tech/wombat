package tech.illuin.wombat.engine.context.environment.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import tech.illuin.wombat.persistence.model.BooleanFlagConverter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "environments")
public class EnvironmentEntity
{
    @Id @Column(nullable = false)
    public String id;

    @Column(nullable = false, unique = true)
    public String uuid;

    @Column(nullable = false)
    public String name;

    @Column
    public String description;

    /**
     * Declared by the monitored-environments file or a worker, and kept in sync by the reconciler: read-only for users.
     * {@code false} for the environments created through the API.
     */
    @Convert(converter = BooleanFlagConverter.class)
    @Column(nullable = false, columnDefinition = "INTEGER")
    public boolean provisioned;

    @Column(name = "created_at", nullable = false, columnDefinition = "INTEGER")
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "INTEGER")
    public Instant updatedAt;

    /** Inactive: skipped by processing, still returned by the API. */
    @Column(name = "disabled_at", columnDefinition = "INTEGER")
    public Instant disabledAt;

    /** Soft delete: ignored everywhere. */
    @Column(name = "deleted_at", columnDefinition = "INTEGER")
    public Instant deletedAt;

    @PrePersist
    void onPersist()
    {
        if (this.uuid == null)
            this.uuid = UUID.randomUUID().toString();
        this.createdAt = now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate()
    {
        this.updatedAt = now();
    }

    /**
     * SQLite stores timestamps as epoch millis, so anything finer would silently disappear on
     * the next read; truncating up front keeps in-memory entities identical to persisted ones.
     */
    public static Instant now()
    {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
