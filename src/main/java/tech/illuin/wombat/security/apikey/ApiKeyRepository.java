package tech.illuin.wombat.security.apikey;

import jakarta.enterprise.context.ApplicationScoped;
import tech.illuin.wombat.persistence.backend.api.repository.Repository;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ApiKeyRepository extends Repository<ApiKeyEntity, String>
{
    public ApiKeyRepository()
    {
        super(ApiKeyEntity.class);
    }

    public Optional<ApiKeyEntity> findByHash(String keyHash)
    {
        List<ApiKeyEntity> results = getEntityManager()
            .createQuery("FROM ApiKeyEntity WHERE keyHash = :keyHash", ApiKeyEntity.class)
            .setParameter("keyHash", keyHash)
            .setMaxResults(1)
            .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<ApiKeyEntity> findAllNewestFirst()
    {
        return getEntityManager()
            .createQuery("FROM ApiKeyEntity ORDER BY createdAt DESC", ApiKeyEntity.class)
            .getResultList();
    }

    public List<ApiKeyEntity> findByOwnerNewestFirst(String ownerId)
    {
        return getEntityManager()
            .createQuery("FROM ApiKeyEntity WHERE ownerId = :ownerId ORDER BY createdAt DESC", ApiKeyEntity.class)
            .setParameter("ownerId", ownerId)
            .getResultList();
    }
}
