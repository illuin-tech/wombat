package tech.illuin.wombat.endpoint;

import io.quarkus.resteasy.reactive.server.EndpointDisabled;
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
import tech.illuin.wombat.commons.NodeModeConfigSourceFactory;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.source.Monitorable;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.engine.context.EnvironmentConflictException;
import tech.illuin.wombat.engine.context.EnvironmentReconciler;
import tech.illuin.wombat.engine.context.model.UnrecognizedAsset;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tech.illuin.wombat.engine.monitor.MonitoredEnvironments;
import tech.illuin.wombat.persistence.backend.push.PushBackendConfig;
import tech.illuin.wombat.security.Permission;

/**
 * Receives the environments declared by workers, so an aggregator evaluates their assets without declaring them itself.
 * Each worker owns the assets it declares, see {@link EnvironmentReconciler}. Only an {@link NodeMode#AGGREGATOR} serves
 * it.
 */
@Path("api/workers")
@EndpointDisabled(name = NodeModeConfigSourceFactory.AGGREGATOR, stringValue = "false")
public class WorkerController
{
    private final EnvironmentReconciler reconciler;
    private final ModuleRegistry modules;

    public WorkerController(
        EnvironmentReconciler reconciler,
        ModuleRegistry modules
    ) {
        this.reconciler = reconciler;
        this.modules = modules;
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
        for (Asset asset : environments.allAssets())
            this.validate(asset);

        try {
            return Response.success(this.reconciler.reconcile(worker, environments.environments()));
        }
        catch (EnvironmentConflictException e) {
            throw new ClientErrorException(e.getMessage(), jakarta.ws.rs.core.Response.Status.CONFLICT);
        }
    }

    /**
     * This instance evaluates the asset, so it needs its module, and a worker only declares what it samples. The
     * asset's secrets are not checked: those of its source are resolved on the worker, and this instance may not know
     * them.
     */
    private void validate(Asset asset)
    {
        if (asset instanceof UnrecognizedAsset unrecognized)
            throw new BadRequestException("Unknown asset type " + unrecognized.rawType() + " for asset " + asset.identity().id());

        if (this.modules.getModule(asset.type()).filter(ModuleMetadata::enabled).isEmpty())
            throw new BadRequestException("No active module for asset type " + asset.type().name() + " of asset " + asset.identity().id());

        if (!(asset instanceof Monitorable))
            throw new BadRequestException("Asset " + asset.identity().id() + " of type " + asset.type().name() + " is not monitorable, declare it in the aggregator's environments file");
    }
}
