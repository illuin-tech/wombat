package tech.illuin.wombat.engine.context.asset.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentEntity;
import tech.illuin.wombat.persistence.model.BooleanFlagConverter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assets")
public class AssetEntity
{
    @Id
    @Column(nullable = false)
    public String id;

    @Column(nullable = false, unique = true)
    public String uuid;

    @Column(name = "environment_id", nullable = false)
    public String environmentId;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String type;

    @Column
    public String description;

    /**
     * Declared by the monitored-environments file or a worker, and kept in sync by the reconciler: read-only for users.
     * {@code false} for the assets created through the API.
     */
    @Convert(converter = BooleanFlagConverter.class)
    @Column(nullable = false, columnDefinition = "INTEGER")
    public boolean provisioned;

    @Convert(converter = AssetConverter.class)
    @Column(name = "properties", nullable = false)
    public Asset properties;

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

    /**
     * Who declares this asset: {@code null} for the monitored-environments file, else the id of the worker which
     * pushed it. Each reconciliation only creates, updates and deletes the assets of its own owner.
     */
    @Column(name = "owner")
    public String owner;

    @PrePersist
    void onPersist()
    {
        if (this.uuid == null)
            this.uuid = UUID.randomUUID().toString();
        this.createdAt = EnvironmentEntity.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate()
    {
        this.updatedAt = EnvironmentEntity.now();
    }

    public Asset toProperties()
    {
        return this.properties;
    }

    public static AssetEntity from(String environmentId, Asset asset)
    {
        AssetEntity entity = new AssetEntity();
        entity.id = asset.identity().id();
        entity.environmentId = environmentId;
        // Assets declared as configuration carry no name of their own, their id stands for it.
        entity.name = asset.identity().id();
        entity.type = asset.identity().assetType();
        entity.properties = asset;
        entity.provisioned = true;
        return entity;
    }
}
