package tech.illuin.wombat.engine.context.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import tech.illuin.wombat.persistence.backend.api.repository.Repository;

import java.util.List;

@ApplicationScoped
public class AssetRepository extends Repository<AssetEntity, String>
{
    public AssetRepository()
    {
        super(AssetEntity.class);
    }

    public List<AssetEntity> findByEnvironment(String environmentId)
    {
        return getEntityManager()
            .createQuery("FROM AssetEntity WHERE environmentId = :environmentId AND deletedAt IS NULL", AssetEntity.class)
            .setParameter("environmentId", environmentId)
            .getResultList();
    }

    public List<AssetEntity> findActive()
    {
        return getEntityManager()
            .createQuery("FROM AssetEntity WHERE deletedAt IS NULL", AssetEntity.class)
            .getResultList();
    }
}
