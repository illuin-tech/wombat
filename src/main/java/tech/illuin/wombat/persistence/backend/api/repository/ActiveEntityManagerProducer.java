package tech.illuin.wombat.persistence.backend.api.repository;

import io.quarkus.hibernate.orm.PersistenceUnit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.illuin.wombat.persistence.BackendMode;

import static tech.illuin.wombat.persistence.PersistenceBackendConfigSourceFactory.ACTIVE_BACKEND_CONFIG_KEY;

@ApplicationScoped
public class ActiveEntityManagerProducer
{
    @Inject @PersistenceUnit("sqlite")
    Instance<EntityManager> sqliteEm;

    @Inject @PersistenceUnit("postgresql")
    Instance<EntityManager> postgresqlEm;

    @ConfigProperty(name = ACTIVE_BACKEND_CONFIG_KEY)
    String activeBackend;

    @Produces
    public EntityManager produceActiveEntityManager()
    {
        BackendMode mode = BackendMode.valueOf(this.activeBackend);

        return switch (mode) {
            case sqlite -> sqliteEm.get();
            case postgresql -> postgresqlEm.get();
        };
    }
}
