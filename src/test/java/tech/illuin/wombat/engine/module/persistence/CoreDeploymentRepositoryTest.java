package tech.illuin.wombat.engine.module.persistence;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class CoreDeploymentRepositoryTest
{
    @Inject
    CoreDeploymentRepository deployments;

    @Inject
    CoreInstanceRepository instances;

    private final List<Long> inserted = new ArrayList<>();

    @AfterEach
    void tearDown()
    {
        QuarkusTransaction.requiringNew().run(() -> {
            this.inserted.forEach(this.deployments::deleteById);
            this.instances.deleteById("stale");
        });
    }

    @Test
    void theStartupRecordedAFirstDeployment()
    {
        assertTrue(this.deployments.findLatest().isPresent());
    }

    @Test
    void insertNext_takesTheNextVersion_andKeepsTheModules()
    {
        long latest = this.deployments.findLatest().orElseThrow().version;

        CoreDeploymentEntity next = this.deployments.insertNext(Set.of("a.b", "c.d"), "alice");
        this.inserted.add(next.version);

        assertEquals(latest + 1, next.version);
        CoreDeploymentEntity read = this.deployments.findLatest().orElseThrow();
        assertEquals(next.id, read.id);
        assertEquals(Set.of("a.b", "c.d"), read.enabledModules);
        assertEquals("alice", read.requestedBy);
    }

    @Test
    void findLive_leavesOutTheInstancesWhichMissedThreeHeartbeats()
    {
        CoreInstanceEntity stale = new CoreInstanceEntity();
        stale.instanceId = "stale";
        stale.deployedVersion = 1;
        stale.deployedAt = Instant.now().minus(Duration.ofHours(1));
        stale.status = CoreInstanceEntity.InstanceStatus.ok;
        stale.detectedModules = Set.of("a.b");
        stale.missingModules = Set.of();
        stale.heartbeatAt = Instant.now().minus(Duration.ofSeconds(91));
        this.instances.upsert(stale);

        List<CoreInstanceEntity> live = this.instances.findLive(Duration.ofSeconds(30));

        assertFalse(live.stream().anyMatch(instance -> instance.instanceId.equals("stale")));
        assertFalse(live.isEmpty(), "this instance wrote its heartbeat at startup");
    }
}
