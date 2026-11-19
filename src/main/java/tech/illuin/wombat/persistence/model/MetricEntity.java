package tech.illuin.wombat.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import tech.illuin.wombat.core.asset.AssetIdentity;

@MappedSuperclass
public abstract class MetricEntity
{
    @Column(name = "asset_id", nullable = false)
    public String assetId;

    @Column(name = "environment_id", nullable = false)
    public String environmentId;

    @Column(name = "asset_type", nullable = false)
    public String assetType;

    public void assign(AssetIdentity asset)
    {
        this.assetId = asset.id();
        this.environmentId = asset.environmentId();
        this.assetType = asset.assetType();
    }

    public AssetIdentity identity()
    {
        return new AssetIdentity(this.assetId, this.environmentId, this.assetType);
    }
}
