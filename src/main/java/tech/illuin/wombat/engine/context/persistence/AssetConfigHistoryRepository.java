package tech.illuin.wombat.engine.context.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import tech.illuin.wombat.persistence.backend.api.repository.Repository;

import java.util.List;

@ApplicationScoped
public class AssetConfigHistoryRepository extends Repository<AssetConfigHistory, Long>
{
    public AssetConfigHistoryRepository()
    {
        super(AssetConfigHistory.class);
    }

    public List<AssetConfigHistory> findByAsset(String assetId)
    {
        return getEntityManager()
            .createQuery("FROM AssetConfigHistory WHERE assetId = :assetId ORDER BY changedAt", AssetConfigHistory.class)
            .setParameter("assetId", assetId)
            .getResultList();
    }
}
