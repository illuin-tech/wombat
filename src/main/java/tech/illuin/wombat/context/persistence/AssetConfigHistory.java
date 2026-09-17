package tech.illuin.wombat.context.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Append-only audit trail of asset configuration changes. One row is written per CREATE/UPDATE/DELETE
 * applied by the startup reconciler; rows are never mutated, and outlive the asset (no foreign key).
 */
@Entity
@Table(name = "asset_config_history")
public class AssetConfigHistory extends PanacheEntityBase
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INTEGER")
    public Long id;

    @Column(name = "asset_id", nullable = false)
    public String assetId;

    @Column(name = "environment_id", nullable = false)
    public String environmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AssetConfigAction action;

    @Column(name = "changed_at", nullable = false, columnDefinition = "INTEGER")
    public Instant changedAt;

    @Column(nullable = false)
    public String snapshot;

    @PrePersist
    void onPersist()
    {
        if (this.changedAt == null)
            this.changedAt = EnvironmentEntity.now();
    }

    public static AssetConfigHistory of(AssetConfigAction action, AssetEntity asset, String snapshot)
    {
        AssetConfigHistory history = new AssetConfigHistory();
        history.assetId = asset.id;
        history.environmentId = asset.environmentId;
        history.action = action;
        history.snapshot = snapshot;
        return history;
    }
}
