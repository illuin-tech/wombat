package tech.illuin.wombat.engine.simulation.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import tech.illuin.wombat.persistence.backend.api.repository.Repository;

import java.util.List;

@ApplicationScoped
public class SimulationRepository extends Repository<SimulationEntity, String>
{
    public SimulationRepository()
    {
        super(SimulationEntity.class);
    }

    public List<SimulationEntity> findAllByName()
    {
        return getEntityManager()
            .createQuery("FROM SimulationEntity ORDER BY name, id", SimulationEntity.class)
            .getResultList();
    }
}
