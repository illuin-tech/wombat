package tech.illuin.wombat.engine.context.environment.persistence;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                if (List.of("env-prod", "env-staging", "env-active", "env-disabled", "env-deleted").contains(e.id))
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
        assertNull(found.deletedAt);
        assertNull(found.description);
        assertFalse(found.provisioned);
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

    @Test
    void visibleExcludesDeleted_activeAlsoExcludesDisabled()
    {
        QuarkusTransaction.requiringNew().run(() -> {
            this.repository.persist(environment("env-active", null, null));
            this.repository.persist(environment("env-disabled", Instant.now(), null));
            this.repository.persist(environment("env-deleted", null, Instant.now()));
        });

        List<String> visible = QuarkusTransaction.requiringNew().call(() -> this.repository.findVisible()).stream().map(e -> e.id).toList();
        List<String> active = QuarkusTransaction.requiringNew().call(() -> this.repository.findActive()).stream().map(e -> e.id).toList();

        assertTrue(visible.containsAll(List.of("env-active", "env-disabled")));
        assertFalse(visible.contains("env-deleted"));
        assertTrue(active.contains("env-active"));
        assertFalse(active.contains("env-disabled"));
        assertFalse(active.contains("env-deleted"));
    }

    @Test
    void findVisibleByIdAndUuidIgnoreDeleted()
    {
        QuarkusTransaction.requiringNew().run(() -> {
            EnvironmentEntity active = environment("env-active", null, null);
            active.description = "Described";
            active.provisioned = true;
            this.repository.persist(active);
            this.repository.persist(environment("env-deleted", null, Instant.now()));
        });

        EnvironmentEntity active = QuarkusTransaction.requiringNew().call(() -> this.repository.findVisibleById("env-active").orElseThrow());
        EnvironmentEntity deleted = QuarkusTransaction.requiringNew().call(() -> this.repository.findById("env-deleted"));

        assertEquals("Described", active.description);
        assertTrue(active.provisioned);
        assertEquals("env-active", QuarkusTransaction.requiringNew().call(() -> this.repository.findVisibleByUuid(active.uuid).orElseThrow()).id);
        assertTrue(QuarkusTransaction.requiringNew().call(() -> this.repository.findVisibleById("env-deleted")).isEmpty());
        assertTrue(QuarkusTransaction.requiringNew().call(() -> this.repository.findVisibleByUuid(deleted.uuid)).isEmpty());
    }

    private static EnvironmentEntity environment(String id, Instant disabledAt, Instant deletedAt)
    {
        EnvironmentEntity entity = new EnvironmentEntity();
        entity.id = id;
        entity.name = id;
        entity.disabledAt = disabledAt;
        entity.deletedAt = deletedAt;
        return entity;
    }
}
