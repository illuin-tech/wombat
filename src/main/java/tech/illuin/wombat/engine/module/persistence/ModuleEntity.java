package tech.illuin.wombat.engine.module.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.FamilyType;
import tech.illuin.wombat.core.asset.type.ModuleType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata.Kind;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentEntity;
import tech.illuin.wombat.persistence.model.BooleanFlagConverter;

import java.time.Instant;

/**
 * The registry state of a module: whether it is enabled, and the type it advertised when last detected. A row is
 * registered for every module an instance detects, and is kept once its module is no longer detected, so that the
 * module shows as missing and keeps its state.
 */
@Entity
@Table(name = "modules")
public class ModuleEntity
{
    /** The module name, which is the asset-type's for an asset-type module, e.g. {@code tech.illuin.wombat-module.kubernetes-api}. */
    @Id @Column(name = "type_name", nullable = false)
    public String typeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "TEXT")
    public Kind kind;

    @Column(nullable = false)
    public String namespace;

    @Column(name = "local_name", nullable = false)
    public String localName;

    /** Null for a family module, which has no asset-type. */
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "TEXT")
    public ActivityRegime regime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "TEXT")
    public ServiceFamily family;

    @Convert(converter = BooleanFlagConverter.class)
    @Column(nullable = false, columnDefinition = "INTEGER")
    public boolean enabled;

    @Column(name = "updated_at", nullable = false, columnDefinition = "INTEGER")
    public Instant updatedAt;

    /** Who last toggled the module; null for a registration, or when authentication is disabled. */
    @Column(name = "updated_by")
    public String updatedBy;

    public static ModuleEntity registered(ModuleType type)
    {
        ModuleEntity entity = new ModuleEntity();
        entity.typeName = type.name();
        entity.enabled = true;
        entity.advertise(type);
        return entity;
    }

    public ModuleType type()
    {
        return switch (this.kind)
        {
            case asset -> new AssetType(this.namespace, this.localName, this.regime, this.family);
            case family -> new FamilyType(this.namespace, this.localName, this.family);
        };
    }

    /**
     * Records the type the detected module advertises, which a later version of its jar may change.
     */
    public void advertise(ModuleType type)
    {
        this.kind = Kind.of(type);
        this.namespace = type.namespace();
        this.localName = type.localName();
        this.regime = type instanceof AssetType assetType ? assetType.regime() : null;
        this.family = type.family();
    }

    @PrePersist
    @PreUpdate
    void onWrite()
    {
        this.updatedAt = EnvironmentEntity.now();
    }
}
