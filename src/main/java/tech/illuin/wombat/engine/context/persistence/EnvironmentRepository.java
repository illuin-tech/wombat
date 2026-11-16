package tech.illuin.wombat.engine.context.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import tech.illuin.wombat.persistence.backend.api.repository.Repository;

import java.util.List;

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
            .createQuery("FROM EnvironmentEntity WHERE disabledAt IS NULL", EnvironmentEntity.class)
            .getResultList();
    }
}
