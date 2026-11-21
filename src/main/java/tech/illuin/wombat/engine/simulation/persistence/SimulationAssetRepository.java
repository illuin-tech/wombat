package tech.illuin.wombat.engine.simulation.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import tech.illuin.wombat.persistence.backend.api.repository.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class SimulationAssetRepository extends Repository<SimulationAssetEntity, SimulationAssetEntity.Key>
{
    public SimulationAssetRepository()
    {
        super(SimulationAssetEntity.class);
    }

    public List<SimulationAssetEntity> findBySimulation(String simulationId)
    {
        return getEntityManager()
            .createQuery("FROM SimulationAssetEntity WHERE simulationId = :simulationId ORDER BY id", SimulationAssetEntity.class)
            .setParameter("simulationId", simulationId)
            .getResultList();
    }

    public Optional<SimulationAssetEntity> findOne(String simulationId, String id)
    {
        return this.findByIdOptional(new SimulationAssetEntity.Key(simulationId, id));
    }

    /**
     * @return the number of assets per simulation id; simulations without any are absent
     */
    public Map<String, Long> countBySimulation()
    {
        return getEntityManager()
            .createQuery("SELECT a.simulationId, COUNT(a) FROM SimulationAssetEntity a GROUP BY a.simulationId", Object[].class)
            .getResultStream()
            .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
    }

    /* SQLite does not enforce the cascade of the foreign key, so a simulation's assets are deleted explicitly */
    public void deleteBySimulation(String simulationId)
    {
        getEntityManager()
            .createQuery("DELETE FROM SimulationAssetEntity WHERE simulationId = :simulationId")
            .setParameter("simulationId", simulationId)
            .executeUpdate();
    }
}
