package tech.illuin.wombat.context.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetType;

import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "assets")
public class AssetEntity extends PanacheEntityBase
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AssetType type;

    @Convert(converter = AssetConverter.class)
    @Column(name = "properties", nullable = false)
    public Asset properties;

    @Column(name = "created_at", nullable = false, columnDefinition = "INTEGER")
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "INTEGER")
    public Instant updatedAt;

    @Column(name = "deleted_at", columnDefinition = "INTEGER")
    public Instant deletedAt;

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

    public static AssetEntity from(String environmentId, Asset properties)
    {
        AssetEntity entity = new AssetEntity();
        entity.id = properties.id();
        entity.environmentId = environmentId;
        entity.name = properties.name();
        entity.type = properties.type();
        entity.properties = properties;
        return entity;
    }
}
