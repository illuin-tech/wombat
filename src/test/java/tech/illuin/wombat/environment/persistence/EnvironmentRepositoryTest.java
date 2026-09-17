package tech.illuin.wombat.environment.persistence;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.context.persistence.EnvironmentEntity;
import tech.illuin.wombat.context.persistence.EnvironmentRepository;

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
        QuarkusTransaction.requiringNew().run(() -> this.repository.delete("id in ?1", List.of("env-prod", "env-staging")));
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

        EnvironmentEntity found = QuarkusTransaction.requiringNew().call(() -> this.repository.findById("env-prod"));
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

        Instant createdAt = QuarkusTransaction.requiringNew().call(() -> this.repository.findById("env-staging")).createdAt;

        QuarkusTransaction.requiringNew().run(() -> {
            EnvironmentEntity entity = this.repository.findById("env-staging");
            entity.disabledAt = Instant.now();
        });

        EnvironmentEntity found = QuarkusTransaction.requiringNew().call(() -> this.repository.findById("env-staging"));
        assertEquals(createdAt, found.createdAt);
        assertNotNull(found.disabledAt);
        assertFalse(found.updatedAt.isBefore(found.createdAt));
    }
}
