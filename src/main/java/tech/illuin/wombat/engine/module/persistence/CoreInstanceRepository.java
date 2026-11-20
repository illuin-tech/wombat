package tech.illuin.wombat.engine.module.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.persistence.backend.api.repository.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class CoreInstanceRepository extends Repository<CoreInstanceEntity, String>
{
    /* An instance which missed this many heartbeats is considered gone */
    private static final int MISSED_HEARTBEATS = 3;

    public CoreInstanceRepository()
    {
        super(CoreInstanceEntity.class);
    }

    @Transactional
    public void upsert(CoreInstanceEntity entity)
    {
        this.entityManager.merge(entity);
    }

    /**
     * @param pollInterval how often instances write their heartbeat
     * @return the instances which wrote a heartbeat lately, ordered by id
     */
    @Transactional
    public List<CoreInstanceEntity> findLive(Duration pollInterval)
    {
        Instant since = Instant.now().minus(pollInterval.multipliedBy(MISSED_HEARTBEATS));
        return getEntityManager()
            .createQuery("FROM CoreInstanceEntity i WHERE i.heartbeatAt >= :since ORDER BY i.instanceId", CoreInstanceEntity.class)
            .setParameter("since", since)
            .getResultList();
    }
}
