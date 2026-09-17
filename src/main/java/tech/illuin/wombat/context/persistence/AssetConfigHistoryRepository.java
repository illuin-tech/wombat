package tech.illuin.wombat.context.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class AssetConfigHistoryRepository implements PanacheRepositoryBase<AssetConfigHistory, Long>
{
    public List<AssetConfigHistory> findByAsset(String assetId)
    {
        return this.list("assetId = ?1 order by changedAt", assetId);
    }
}
