package tech.illuin.wombat.endpoint;

import io.quarkus.resteasy.reactive.server.EndpointDisabled;
import io.quarkus.security.PermissionsAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.engine.context.EnvironmentReconciler;
import tech.illuin.wombat.engine.context.WorkerEnvironmentService;
import tech.illuin.wombat.engine.context.asset.AssetConflictException;
import tech.illuin.wombat.engine.context.asset.InvalidAssetException;
import tech.illuin.wombat.engine.context.environment.EnvironmentConflictException;
import tech.illuin.wombat.engine.monitor.MonitoredEnvironments;
import tech.illuin.wombat.feature.Feature;
import tech.illuin.wombat.security.Permission;

import static jakarta.ws.rs.core.Response.Status.CONFLICT;

/**
 * The environments of this worker declared by a process next to it, on top of those of its environments file.
 * Served by a worker with {@code wombat.worker.environments.api=true}, see {@link WorkerEnvironmentService}.
 */
@Path("api/worker/environments")
@EndpointDisabled(name = Feature.Keys.WORKER_ENVIRONMENTS, stringValue = "false")
public class WorkerEnvironmentController
{
    private static final Logger logger = LoggerFactory.getLogger(WorkerEnvironmentController.class);

    private final WorkerEnvironmentService service;

    public WorkerEnvironmentController(WorkerEnvironmentService service)
    {
        this.service = service;
    }

    @GET
    @PermissionsAllowed(Permission.Names.API_ENVIRONMENT_READ)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = MonitoredEnvironments.class)
    })))
    @APIResponse(responseCode = "404", description = "Nothing declared since the worker started")
    public Response<MonitoredEnvironments> get()
    {
        /* Only what was declared here, without the file's environments */
        return Response.success(this.service.declared().orElseThrow(() -> new NotFoundException("Nothing declared since the worker started")));
    }

    /**
     * Replaces the environments declared through this endpoint, with the same content as an environments file: its assets
     * missing from the request are deleted, those of the environments file are left as they are. The worker samples them from its next heartbeat, and declares them to its aggregator
     * within a minute.
     */
    @PUT
    @PermissionsAllowed(Permission.Names.API_ENVIRONMENT_WRITE)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = EnvironmentReconciler.Result.class)
    })))
    @APIResponse(responseCode = "400", description = "An asset cannot be sampled here: unknown type, not monitorable, or missing a secret")
    @APIResponse(responseCode = "409", description = "An asset id is already declared by the environments file, or an id clashes with an environment or asset created through the API")
    public Response<EnvironmentReconciler.Result> declare(@NotNull @Valid MonitoredEnvironments environments)
    {
        try {
            return Response.success(this.service.declare(environments));
        }
        /* Logged: the 400 or 409 carries no message, and whatever declares the environments runs unattended */
        catch (InvalidAssetException e) {
            logger.warn("Rejected environments declaration: {}", e.getMessage());
            throw new BadRequestException(e.getMessage());
        }
        catch (EnvironmentConflictException | AssetConflictException e) {
            logger.warn("Rejected environments declaration: {}", e.getMessage());
            throw new ClientErrorException(e.getMessage(), CONFLICT);
        }
    }
}
