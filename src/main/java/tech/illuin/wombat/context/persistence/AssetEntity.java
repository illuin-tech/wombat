package tech.illuin.wombat.context.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import tech.illuin.wombat.context.model.UnrecognizedAsset;
import tech.illuin.wombat.core.asset.Asset;

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

    @Column(nullable = false)
    public String type;

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
        if (this.properties instanceof UnrecognizedAsset unrecognized && unrecognized.rawType() == null)
            return new UnrecognizedAsset(unrecognized.identity(), this.type, unrecognized.rawJson());
        return this.properties;
    }

    public static AssetEntity from(String environmentId, Asset asset)
    {
        AssetEntity entity = new AssetEntity();
        entity.id = asset.identity().id();
        entity.environmentId = environmentId;
        entity.name = asset.identity().name();
        entity.type = asset instanceof UnrecognizedAsset unrecognized
            ? unrecognized.rawType()
            : asset.type().name()
        ;
        entity.properties = asset;
        return entity;
    }
}
