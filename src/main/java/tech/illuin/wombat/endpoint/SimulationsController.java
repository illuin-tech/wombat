package tech.illuin.wombat.endpoint;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.resteasy.reactive.server.EndpointDisabled;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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
import tech.illuin.wombat.engine.context.environment.UnknownEnvironmentException;
import tech.illuin.wombat.engine.simulation.SimulationAssetService;
import tech.illuin.wombat.engine.simulation.SimulationConflictException;
import tech.illuin.wombat.engine.simulation.SimulationPeriod;
import tech.illuin.wombat.engine.simulation.SimulationService;
import tech.illuin.wombat.engine.simulation.UnknownSimulationException;
import tech.illuin.wombat.engine.simulation.model.SimulationInfo;
import tech.illuin.wombat.engine.simulation.model.SimulationSummary;
import tech.illuin.wombat.engine.simulation.persistence.SimulationEntity;
import tech.illuin.wombat.feature.Feature;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;

import java.util.List;
import java.util.Map;

import static jakarta.ws.rs.core.Response.Status.CONFLICT;

/**
 * The simulations: sandboxes of asset definitions, evaluated on demand and never monitored. Not to be confused with
 * {@link SimulationController}, the stateless evaluation of ad-hoc payloads.
 */
@Path("api/simulations")
@EndpointDisabled(name = Feature.Keys.API_READ, stringValue = "false")
@PermissionsAllowed(Permission.Names.API_SIMULATION_READ)
public class SimulationsController
{
    private final SimulationService service;
    private final SimulationAssetService assetService;
    private final SecurityIdentity identity;

    public SimulationsController(SimulationService service, SimulationAssetService assetService, SecurityIdentity identity)
    {
        this.service = service;
        this.assetService = assetService;
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
                    @SchemaProperty(name = "payload", type = SchemaType.ARRAY, implementation = SimulationSummary.class)
                }
            )
        )
    )
    public Response<List<SimulationSummary>> list()
    {
        Map<String, Long> counts = this.service.assetCounts();
        Map<String, String> environmentNames = this.service.environmentNames();
        return Response.success(
            this.service.list().stream()
                .map(simulation -> SimulationSummary.from(
                    simulation,
                    simulation.parentEnvironmentId == null ? null : environmentNames.get(simulation.parentEnvironmentId),
                    counts.getOrDefault(simulation.id, 0L)
                ))
                .toList()
        );
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = SimulationInfo.class)
    })))
    @APIResponse(responseCode = "404", description = "Unknown simulation")
    public Response<SimulationInfo> get(@PathParam("id") String id)
    {
        try {
            return Response.success(this.toInfo(this.service.get(id)));
        }
        catch (UnknownSimulationException e) {
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
        @SchemaProperty(name = "payload", implementation = SimulationInfo.class)
    })))
    @APIResponse(responseCode = "400", description = "Invalid payload, or an asset that cannot be copied")
    @APIResponse(responseCode = "403", description = "Copying assets also requires " + Permission.Names.API_ASSET_READ)
    @APIResponse(responseCode = "404", description = "Unknown parent environment, or asset in it")
    @APIResponse(responseCode = "409", description = "A simulation with this id already exists")
    public Response<SimulationInfo> create(@NotNull @Valid SimulationCreateRequest request)
    {
        if (!request.assetIds().isEmpty())
            this.requireAssetRead();
        try {
            return Response.success(this.toInfo(this.service.create(
                request.id(), request.name(), request.description(), request.notes(), request.referenceWindow(),
                request.parentEnvironmentId(), request.assetIds()
            )));
        }
        catch (UnknownEnvironmentException | UnknownAssetException e) {
            throw new NotFoundException(e.getMessage());
        }
        catch (SimulationConflictException e) {
            throw new ClientErrorException(e.getMessage(), CONFLICT);
        }
        catch (InvalidAssetException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @PUT
    @Path("/{id}")
    @PermissionsAllowed(Permission.Names.API_SIMULATION_WRITE)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = SimulationInfo.class)
    })))
    @APIResponse(responseCode = "400", description = "Invalid payload")
    @APIResponse(responseCode = "404", description = "Unknown simulation")
    public Response<SimulationInfo> update(@PathParam("id") String id, @NotNull @Valid SimulationUpdateRequest request)
    {
        try {
            return Response.success(this.toInfo(this.service.update(id, request.name(), request.description(), request.notes(), request.referenceWindow())));
        }
        catch (UnknownSimulationException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @POST
    @Path("/{id}/duplicate")
    @PermissionsAllowed(Permission.Names.API_SIMULATION_WRITE)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    @APIResponse(responseCode = "201", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = SimulationInfo.class)
    })))
    @APIResponse(responseCode = "400", description = "Invalid payload")
    @APIResponse(responseCode = "404", description = "Unknown simulation")
    @APIResponse(responseCode = "409", description = "A simulation with this id already exists")
    public Response<SimulationInfo> duplicate(@PathParam("id") String id, @NotNull @Valid SimulationDuplicateRequest request)
    {
        try {
            return Response.success(this.toInfo(this.service.duplicate(id, request.id(), request.name())));
        }
        catch (UnknownSimulationException e) {
            throw new NotFoundException(e.getMessage());
        }
        catch (SimulationConflictException e) {
            throw new ClientErrorException(e.getMessage(), CONFLICT);
        }
    }

    @DELETE
    @Path("/{id}")
    @PermissionsAllowed(Permission.Names.API_SIMULATION_WRITE)
    @APIResponse(responseCode = "204", description = "Deleted, with its assets")
    @APIResponse(responseCode = "404", description = "Unknown simulation")
    public void delete(@PathParam("id") String id)
    {
        try {
            this.service.delete(id);
        }
        catch (UnknownSimulationException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    /* Copying an asset reads it, which the caller may not be allowed to */
    private void requireAssetRead()
    {
        if (!Caller.permissions(this.identity).contains(Permission.API_ASSET_READ))
            throw new ForbiddenException("Copying assets requires the permission " + Permission.Names.API_ASSET_READ);
    }

    private SimulationInfo toInfo(SimulationEntity entity)
    {
        return SimulationInfo.from(
            entity,
            this.service.parentEnvironmentName(entity).orElse(null),
            this.assetService.describe(this.assetService.list(entity.id))
        );
    }

    /**
     * @param assetIds the assets of {@code parentEnvironmentId} to copy, none when empty
     */
    public record SimulationCreateRequest(
        @NotBlank @Size(max = SlugHelper.MAX_LENGTH) @Pattern(regexp = SlugHelper.PATTERN, message = SlugHelper.MESSAGE) @JsonProperty("id") @Schema(required = true) String id,
        @NotBlank @Size(max = 255) @JsonProperty("name") @Schema(required = true) String name,
        @Size(max = 4096) @JsonProperty("description") String description,
        @Size(max = 65536) @JsonProperty("notes") String notes,
        @JsonProperty("referenceWindow") @Schema(defaultValue = "MONTH") SimulationPeriod referenceWindow,
        @JsonProperty("parentEnvironmentId") String parentEnvironmentId,
        @JsonProperty("assetIds") List<@NotBlank String> assetIds
    ) {
        public SimulationCreateRequest
        {
            assetIds = assetIds != null ? assetIds : List.of();
        }

        @JsonIgnore
        @AssertTrue(message = "assets are copied from a parent environment, which is missing")
        public boolean isAssetsWithParent()
        {
            return this.assetIds.isEmpty() || this.parentEnvironmentId != null;
        }
    }

    /**
     * Replaces the editable fields of a simulation; its id and parent environment are immutable.
     */
    public record SimulationUpdateRequest(
        @NotBlank @Size(max = 255) @JsonProperty("name") @Schema(required = true) String name,
        @Size(max = 4096) @JsonProperty("description") String description,
        @Size(max = 65536) @JsonProperty("notes") String notes,
        @NotNull @JsonProperty("referenceWindow") @Schema(required = true) SimulationPeriod referenceWindow
    ) {}

    public record SimulationDuplicateRequest(
        @NotBlank @Size(max = SlugHelper.MAX_LENGTH) @Pattern(regexp = SlugHelper.PATTERN, message = SlugHelper.MESSAGE) @JsonProperty("id") @Schema(required = true) String id,
        @NotBlank @Size(max = 255) @JsonProperty("name") @Schema(required = true) String name
    ) {}
}
