package tech.illuin.wombat.engine.context.asset.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import tech.illuin.wombat.persistence.backend.api.repository.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Visible assets are those not soft-deleted, as the API and the reconciler see them; active ones are also not
 * disabled, as processing sees them.
 */
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
            .createQuery("FROM AssetEntity WHERE environmentId = :environmentId AND deletedAt IS NULL AND disabledAt IS NULL", AssetEntity.class)
            .setParameter("environmentId", environmentId)
            .getResultList();
    }

    public List<AssetEntity> findVisibleByEnvironment(String environmentId)
    {
        return getEntityManager()
            .createQuery("FROM AssetEntity WHERE environmentId = :environmentId AND deletedAt IS NULL", AssetEntity.class)
            .setParameter("environmentId", environmentId)
            .getResultList();
    }

    public long countVisibleByEnvironment(String environmentId)
    {
        return getEntityManager()
            .createQuery("SELECT COUNT(a) FROM AssetEntity a WHERE a.environmentId = :environmentId AND a.deletedAt IS NULL", Long.class)
            .setParameter("environmentId", environmentId)
            .getSingleResult();
    }

    /**
     * @return the number of visible assets per asset-type name; types without any are absent
     */
    public Map<String, Long> countVisibleByType()
    {
        return getEntityManager()
            .createQuery("SELECT a.type, COUNT(a) FROM AssetEntity a WHERE a.deletedAt IS NULL GROUP BY a.type", Object[].class)
            .getResultStream()
            .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
    }

    public List<AssetEntity> findVisible()
    {
        return getEntityManager()
            .createQuery("FROM AssetEntity WHERE deletedAt IS NULL", AssetEntity.class)
            .getResultList();
    }

    public Optional<AssetEntity> findVisibleById(String id)
    {
        return getEntityManager()
            .createQuery("FROM AssetEntity WHERE id = :id AND deletedAt IS NULL", AssetEntity.class)
            .setParameter("id", id)
            .getResultStream()
            .findFirst();
    }

    public Optional<AssetEntity> findVisibleByUuid(String uuid)
    {
        return getEntityManager()
            .createQuery("FROM AssetEntity WHERE uuid = :uuid AND deletedAt IS NULL", AssetEntity.class)
            .setParameter("uuid", uuid)
            .getResultStream()
            .findFirst();
    }
}
