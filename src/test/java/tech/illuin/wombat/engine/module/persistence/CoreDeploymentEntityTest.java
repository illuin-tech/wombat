package tech.illuin.wombat.engine.module.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreDeploymentEntityTest
{
    private static final Instant REQUESTED_AT = Instant.parse("2026-11-20T14:02:00Z");

    @Test
    void theIdIsAHexSha256()
    {
        String id = CoreDeploymentEntity.of(7, REQUESTED_AT, "alice", Set.of("a.b")).id;

        assertEquals(64, id.length());
        assertTrue(id.matches("[0-9a-f]+"), id);
    }

    @Test
    void theSameInputsGiveTheSameId_whateverTheModuleOrder()
    {
        assertEquals(
            CoreDeploymentEntity.of(7, REQUESTED_AT, "alice", Set.of("a.b", "c.d")).id,
            CoreDeploymentEntity.of(7, REQUESTED_AT, "bob", Set.of("c.d", "a.b")).id
        );
    }

    @Test
    void twoDeploymentsOfTheSameModulesGetDifferentIds()
    {
        assertNotEquals(
            CoreDeploymentEntity.of(7, REQUESTED_AT, null, Set.of("a.b")).id,
            CoreDeploymentEntity.of(8, REQUESTED_AT, null, Set.of("a.b")).id
        );
    }
}
