package tech.illuin.wombat.engine.module;

import tech.illuin.wombat.engine.module.persistence.CoreInstanceEntity;
import tech.illuin.wombat.engine.module.runtime.CoreDeploymentService;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * The core deployment as the API serves it: the latest one requested, which instances run it, and the toggles it does
 * not hold yet.
 *
 * @param pending             the modules whose registry state the latest deployment does not hold yet
 * @param pollIntervalSeconds how often instances poll for the latest deployment
 */
public record DeploymentInfo(Latest latest, List<Instance> instances, List<PendingModule> pending, long pollIntervalSeconds)
{
    /**
     * @param id      the full hash: clients shorten it to 7 characters, like a commit id
     * @param modules the modules enabled when it was requested
     */
    public record Latest(long version, String id, Instant requestedAt, String requestedBy, Set<String> modules) {}

    /**
     * @param deployedId null if the deployment the instance runs is unknown here
     * @param current    whether this is the instance serving the request
     */
    public record Instance(
        String instanceId,
        long deployedVersion,
        String deployedId,
        Instant deployedAt,
        CoreInstanceEntity.Status status,
        String error,
        Set<String> detectedModules,
        Set<String> missingModules,
        boolean current
    ) {}

    public record PendingModule(String type, boolean enabled) {}

    /**
     * A redeploy recorded, and the outcome of deploying it on the instance which recorded it; the others follow on
     * their next poll.
     */
    public record Redeployed(long version, String id, CoreDeploymentService.Result local) {}
}
