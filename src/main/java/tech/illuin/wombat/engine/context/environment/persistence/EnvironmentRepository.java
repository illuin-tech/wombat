package tech.illuin.wombat.engine.context.environment.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import tech.illuin.wombat.persistence.backend.api.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Visible environments are those not soft-deleted, as the API sees them; active ones are also not disabled, as
 * processing sees them.
 */
@ApplicationScoped
public class EnvironmentRepository extends Repository<EnvironmentEntity, String>
{
    public EnvironmentRepository()
    {
        super(EnvironmentEntity.class);
    }

    public List<EnvironmentEntity> findActive()
    {
        return getEntityManager()
            .createQuery("FROM EnvironmentEntity WHERE deletedAt IS NULL AND disabledAt IS NULL", EnvironmentEntity.class)
            .getResultList();
    }

    public List<EnvironmentEntity> findVisible()
    {
        return getEntityManager()
            .createQuery("FROM EnvironmentEntity WHERE deletedAt IS NULL", EnvironmentEntity.class)
            .getResultList();
    }

    public Optional<EnvironmentEntity> findVisibleById(String id)
    {
        return getEntityManager()
            .createQuery("FROM EnvironmentEntity WHERE id = :id AND deletedAt IS NULL", EnvironmentEntity.class)
            .setParameter("id", id)
            .getResultStream()
            .findFirst();
    }

    public Optional<EnvironmentEntity> findVisibleByUuid(String uuid)
    {
        return getEntityManager()
            .createQuery("FROM EnvironmentEntity WHERE uuid = :uuid AND deletedAt IS NULL", EnvironmentEntity.class)
            .setParameter("uuid", uuid)
            .getResultStream()
            .findFirst();
    }
}
