package tech.illuin.wombat.engine.module.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentEntity;
import tech.illuin.wombat.persistence.backend.api.repository.Repository;

import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class CoreDeploymentRepository extends Repository<CoreDeploymentEntity, Long>
{
    public CoreDeploymentRepository()
    {
        super(CoreDeploymentEntity.class);
    }

    @Transactional
    public Optional<CoreDeploymentEntity> findLatest()
    {
        return getEntityManager()
            .createQuery("FROM CoreDeploymentEntity d ORDER BY d.version DESC", CoreDeploymentEntity.class)
            .setMaxResults(1)
            .getResultStream()
            .findFirst();
    }

    /**
     * Records a deployment of {@code enabledModules} under the next version.
     *
     * @throws jakarta.persistence.PersistenceException if another instance recorded that version in the meantime
     */
    @Transactional
    public CoreDeploymentEntity insertNext(Set<String> enabledModules, String requestedBy)
    {
        long latest = getEntityManager()
            .createQuery("SELECT COALESCE(MAX(d.version), 0) FROM CoreDeploymentEntity d", Long.class)
            .getSingleResult();

        CoreDeploymentEntity entity = CoreDeploymentEntity.of(latest + 1, EnvironmentEntity.now(), requestedBy, enabledModules);
        this.entityManager.persist(entity);
        /* Flushed here, so that a concurrent insert surfaces as a PersistenceException rather than at commit */
        this.entityManager.flush();
        return entity;
    }
}
