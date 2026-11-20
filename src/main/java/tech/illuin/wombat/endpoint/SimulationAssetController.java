package tech.illuin.wombat.endpoint;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.resteasy.reactive.server.EndpointDisabled;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.resteasy.reactive.ResponseStatus;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.engine.context.SlugHelper;
import tech.illuin.wombat.engine.context.asset.InvalidAssetException;
import tech.illuin.wombat.engine.context.asset.UnknownAssetException;
import tech.illuin.wombat.engine.simulation.SimulationAssetConflictException;
import tech.illuin.wombat.engine.simulation.SimulationAssetService;
import tech.illuin.wombat.engine.simulation.UnknownSimulationAssetException;
import tech.illuin.wombat.engine.simulation.UnknownSimulationException;
import tech.illuin.wombat.engine.simulation.model.SimulationAssetInfo;
import tech.illuin.wombat.engine.simulation.persistence.SimulationAssetEntity;
import tech.illuin.wombat.feature.Feature;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;

import java.util.List;

import static jakarta.ws.rs.core.Response.Status.CONFLICT;

/**
 * The asset definitions of a simulation, whose ids are unique within it only. A copy of a real asset is a snapshot: it
 * is synced from its parent on demand.
 */
@Path("api/simulations/{simulationId}/assets")
@EndpointDisabled(name = Feature.Keys.API_READ, stringValue = "false")
@PermissionsAllowed(Permission.Names.API_SIMULATION_READ)
public class SimulationAssetController
{
    private final SimulationAssetService service;
    private final SecurityIdentity identity;

    public SimulationAssetController(SimulationAssetService service, SecurityIdentity identity)
    {
        this.service = service;
        this.identity = identity;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(
        responseCode = "200",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                properties = {
                    @SchemaProperty(name = "status", implementation = Status.class),
                    @SchemaProperty(name = "payload", type = SchemaType.ARRAY, implementation = SimulationAssetInfo.class)
                }
            )
        )
    )
    @APIResponse(responseCode = "404", description = "Unknown simulation")
    public Response<List<SimulationAssetInfo>> list(@PathParam("simulationId") String simulationId)
    {
        try {
            return Response.success(this.service.describe(this.service.list(simulationId)));
        }
        catch (UnknownSimulationException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @GET
    @Path("/{assetId}")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = SimulationAssetInfo.class)
    })))
    @APIResponse(responseCode = "404", description = "Unknown simulation or asset")
    public Response<SimulationAssetInfo> get(@PathParam("simulationId") String simulationId, @PathParam("assetId") String assetId)
    {
        try {
            return Response.success(this.toInfo(this.service.get(simulationId, assetId)));
        }
        catch (UnknownSimulationException | UnknownSimulationAssetException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @POST
    @PermissionsAllowed(Permission.Names.API_SIMULATION_WRITE)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    @APIResponse(responseCode = "201", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = SimulationAssetInfo.class)
    })))
    @APIResponse(responseCode = "400", description = "Invalid payload, unknown asset type or invalid properties")
    @APIResponse(responseCode = "403", description = "Copying an asset also requires " + Permission.Names.API_ASSET_READ)
    @APIResponse(responseCode = "404", description = "Unknown simulation, or parent asset")
    @APIResponse(responseCode = "409", description = "An asset with this id already exists in the simulation")
    public Response<SimulationAssetInfo> create(@PathParam("simulationId") String simulationId, @NotNull @Valid SimulationAssetCreateRequest request)
    {
        if (request.parentAssetId() != null)
            this.requireAssetRead();
        try {
            return Response.success(this.toInfo(this.service.create(
                simulationId, request.id(), request.name(), request.description(), request.notes(),
                request.parentAssetId(), request.type(), request.properties()
            )));
        }
        catch (UnknownSimulationException | UnknownAssetException e) {
            throw new NotFoundException(e.getMessage());
        }
        catch (SimulationAssetConflictException e) {
            throw new ClientErrorException(e.getMessage(), CONFLICT);
        }
        catch (InvalidAssetException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @PUT
    @Path("/{assetId}")
    @PermissionsAllowed(Permission.Names.API_SIMULATION_WRITE)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = SimulationAssetInfo.class)
    })))
    @APIResponse(responseCode = "400", description = "Invalid payload or properties")
    @APIResponse(responseCode = "404", description = "Unknown simulation or asset")
    public Response<SimulationAssetInfo> update(
        @PathParam("simulationId") String simulationId,
        @PathParam("assetId") String assetId,
        @NotNull @Valid SimulationAssetUpdateRequest request
    ) {
        try {
            return Response.success(this.toInfo(this.service.update(
                simulationId, assetId, request.name(), request.description(), request.notes(), request.properties()
            )));
        }
        catch (UnknownSimulationException | UnknownSimulationAssetException e) {
            throw new NotFoundException(e.getMessage());
        }
        catch (InvalidAssetException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @DELETE
    @Path("/{assetId}")
    @PermissionsAllowed(Permission.Names.API_SIMULATION_WRITE)
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "404", description = "Unknown simulation or asset")
    public void delete(@PathParam("simulationId") String simulationId, @PathParam("assetId") String assetId)
    {
        try {
            this.service.delete(simulationId, assetId);
        }
        catch (UnknownSimulationException | UnknownSimulationAssetException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @POST
    @Path("/{assetId}/sync")
    @PermissionsAllowed(Permission.Names.API_SIMULATION_WRITE)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = SimulationAssetInfo.class)
    })))
    @APIResponse(responseCode = "403", description = "Syncing also requires " + Permission.Names.API_ASSET_READ)
    @APIResponse(responseCode = "404", description = "Unknown simulation or asset")
    @APIResponse(responseCode = "409", description = "The asset has no parent, or it was deleted")
    public Response<SimulationAssetInfo> sync(@PathParam("simulationId") String simulationId, @PathParam("assetId") String assetId)
    {
        this.requireAssetRead();
        try {
            return Response.success(this.toInfo(this.service.sync(simulationId, assetId)));
        }
        catch (UnknownSimulationException | UnknownSimulationAssetException e) {
            throw new NotFoundException(e.getMessage());
        }
        catch (SimulationAssetConflictException e) {
            throw new ClientErrorException(e.getMessage(), CONFLICT);
        }
    }

    /* Copying an asset reads it, which the caller may not be allowed to */
    private void requireAssetRead()
    {
        if (!Caller.permissions(this.identity).contains(Permission.API_ASSET_READ))
            throw new ForbiddenException("Copying an asset requires the permission " + Permission.Names.API_ASSET_READ);
    }

    private SimulationAssetInfo toInfo(SimulationAssetEntity entity)
    {
        return this.service.describe(List.of(entity)).getFirst();
    }

    /**
     * Copies {@code parentAssetId} when given: its type, and its properties unless {@code properties} are given.
     * Without a parent, {@code id}, {@code type} and {@code properties} are required. The id of a copy defaults to its
     * parent's.
     */
    public record SimulationAssetCreateRequest(
        @Size(max = SlugHelper.MAX_LENGTH) @Pattern(regexp = SlugHelper.PATTERN, message = SlugHelper.MESSAGE) @JsonProperty("id") String id,
        @NotBlank @Size(max = 255) @JsonProperty("name") @Schema(required = true) String name,
        @Size(max = 4096) @JsonProperty("description") String description,
        @Size(max = 65536) @JsonProperty("notes") String notes,
        @JsonProperty("parentAssetId") String parentAssetId,
        @JsonProperty("type") String type,
        @JsonProperty("properties") ObjectNode properties
    ) {}

    /**
     * Replaces the editable fields of a simulation asset; its id and type are immutable.
     */
    public record SimulationAssetUpdateRequest(
        @NotBlank @Size(max = 255) @JsonProperty("name") @Schema(required = true) String name,
        @Size(max = 4096) @JsonProperty("description") String description,
        @Size(max = 65536) @JsonProperty("notes") String notes,
        @NotNull @JsonProperty("properties") @Schema(required = true) ObjectNode properties
    ) {}
}
