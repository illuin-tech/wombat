package tech.illuin.wombat.endpoint;

import io.quarkus.security.PermissionsAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import tech.illuin.wombat.commons.NodeMode;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.secret.SecretAware;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.engine.context.EnvironmentConflictException;
import tech.illuin.wombat.engine.context.EnvironmentReconciler;
import tech.illuin.wombat.engine.context.model.UnrecognizedAsset;
import tech.illuin.wombat.engine.monitor.MonitoredEnvironments;
import tech.illuin.wombat.persistence.backend.push.PushBackendConfig;
import tech.illuin.wombat.security.Permission;

import java.util.List;

/**
 * Receives the environments declared by workers, so an aggregator evaluates their assets without declaring them itself.
 * Each worker owns the assets it declares, see {@link EnvironmentReconciler}. Only an {@link NodeMode#AGGREGATOR} serves
 * it.
 */
@Path("api/workers")
public class WorkerController
{
    private final NodeMode mode;
    private final EnvironmentReconciler reconciler;
    private final SecretResolver secrets;

    public WorkerController(
        WombatProperties wombat,
        EnvironmentReconciler reconciler,
        SecretResolver secrets
    ) {
        this.mode = wombat.mode();
        this.reconciler = reconciler;
        this.secrets = secrets;
    }

    /**
     * Replaces the environments declared by a worker: its assets missing from the request are deleted.
     */
    @PUT
    @Path("/{worker}/environments")
    @PermissionsAllowed(Permission.Names.API_ENVIRONMENT_WRITE)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(
        responseCode = "200",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                properties = {
                    @SchemaProperty(name = "status", implementation = Status.class),
                    @SchemaProperty(name = "payload", implementation = EnvironmentReconciler.Result.class)
                }
            )
        )
    )
    @APIResponse(responseCode = "409", description = "An asset is already declared by the aggregator or another worker")
    public Response<EnvironmentReconciler.Result> declare(
        @PathParam("worker") @Pattern(regexp = PushBackendConfig.WORKER_ID_PATTERN) String worker,
        @NotNull @Valid MonitoredEnvironments environments
    ) {
        if (this.mode != NodeMode.AGGREGATOR)
            throw new NotFoundException("Environments can only be declared to an instance in aggregator mode");

        for (Asset asset : environments.allAssets())
            this.validate(asset);

        try {
            return Response.success(this.reconciler.reconcile(worker, environments.environments()));
        }
        catch (EnvironmentConflictException e) {
            throw new ClientErrorException(e.getMessage(), jakarta.ws.rs.core.Response.Status.CONFLICT);
        }
    }

    /* This instance evaluates the asset: it needs its module, and the credentials its evaluation may use. */
    private void validate(Asset asset)
    {
        if (asset instanceof UnrecognizedAsset unrecognized)
            throw new BadRequestException("Unknown asset type " + unrecognized.rawType() + " for asset " + asset.identity().id());

        if (asset instanceof SecretAware secretAware)
        {
            List<String> missing = secretAware.requiredSecretKeys().stream()
                .filter(key -> this.secrets.find(key).isEmpty())
                .toList();
            if (!missing.isEmpty())
                throw new BadRequestException("Asset " + asset.identity().id() + " requires secret(s) missing from this instance: " + String.join(", ", missing));
        }
    }
}
