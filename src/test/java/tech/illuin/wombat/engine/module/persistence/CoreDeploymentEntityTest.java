package tech.illuin.wombat.engine.module.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreDeploymentEntityTest
{
    private static final Instant REQUESTED_AT = Instant.parse("2026-11-20T14:02:00Z");

    @Test
    void theIdIsAHexSha256()
    {
        String id = CoreDeploymentEntity.of(7, REQUESTED_AT, "alice", List.of("a.b")).id;

        assertEquals(64, id.length());
        assertTrue(id.matches("[0-9a-f]+"), id);
    }

    @Test
    void theSameInputsGiveTheSameId_whoeverRequestedIt()
    {
        assertEquals(
            CoreDeploymentEntity.of(7, REQUESTED_AT, "alice", List.of("a.b", "c.d")).id,
            CoreDeploymentEntity.of(7, REQUESTED_AT, "bob", List.of("a.b", "c.d")).id
        );
    }

    @Test
    void theModulesKeepTheirOrder_whichIsPartOfTheId()
    {
        CoreDeploymentEntity deployment = CoreDeploymentEntity.of(7, REQUESTED_AT, null, List.of("c.d", "a.b"));

        assertEquals(List.of("c.d", "a.b"), deployment.enabledModules);
        assertNotEquals(CoreDeploymentEntity.of(7, REQUESTED_AT, null, List.of("a.b", "c.d")).id, deployment.id);
    }

    @Test
    void twoDeploymentsOfTheSameModulesGetDifferentIds()
    {
        assertNotEquals(
            CoreDeploymentEntity.of(7, REQUESTED_AT, null, List.of("a.b")).id,
            CoreDeploymentEntity.of(8, REQUESTED_AT, null, List.of("a.b")).id
        );
    }
}
