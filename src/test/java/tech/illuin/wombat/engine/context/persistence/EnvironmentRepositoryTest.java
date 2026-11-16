package tech.illuin.wombat.engine.context.persistence;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.context.persistence.EnvironmentEntity;
import tech.illuin.wombat.engine.context.persistence.EnvironmentRepository;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class EnvironmentRepositoryTest
{

    @Inject
    EnvironmentRepository repository;

    @BeforeEach
    @AfterEach
    void clean()
    {
        QuarkusTransaction.requiringNew().run(() -> {
            for (EnvironmentEntity e : this.repository.listAll())
                if (List.of("env-prod", "env-staging").contains(e.id))
                    this.repository.delete(e);
        });
    }

    @Test
    void persistSetsTimestampsAndRoundTrips()
    {
        QuarkusTransaction.requiringNew().run(() -> {
            EnvironmentEntity entity = new EnvironmentEntity();
            entity.id = "env-prod";
            entity.name = "Production";
            this.repository.persist(entity);
        });

        EnvironmentEntity found = QuarkusTransaction.requiringNew().call(() -> this.repository.findByIdOptional("env-prod").orElse(null));
        assertNotNull(found);
        assertNotNull(found.uuid);
        assertEquals("Production", found.name);
        assertNotNull(found.createdAt);
        assertEquals(found.createdAt, found.updatedAt);
        assertNull(found.disabledAt);
    }

    @Test
    void updateBumpsUpdatedAtAndDisableIsRecorded()
    {
        QuarkusTransaction.requiringNew().run(() -> {
            EnvironmentEntity entity = new EnvironmentEntity();
            entity.id = "env-staging";
            entity.name = "Staging";
            this.repository.persist(entity);
        });

        Instant createdAt = QuarkusTransaction.requiringNew().call(() -> this.repository.findByIdOptional("env-staging").orElse(null)).createdAt;

        QuarkusTransaction.requiringNew().run(() -> {
            EnvironmentEntity entity = this.repository.findByIdOptional("env-staging").orElse(null);
            entity.disabledAt = Instant.now();
        });

        EnvironmentEntity found = QuarkusTransaction.requiringNew().call(() -> this.repository.findByIdOptional("env-staging").orElse(null));
        assertEquals(createdAt, found.createdAt);
        assertNotNull(found.disabledAt);
        assertFalse(found.updatedAt.isBefore(found.createdAt));
    }
}
