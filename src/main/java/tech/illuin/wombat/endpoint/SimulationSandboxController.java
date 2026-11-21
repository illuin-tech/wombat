package tech.illuin.wombat.endpoint;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.resteasy.reactive.ResponseStatus;
import tech.illuin.wombat.core.evaluation.AssetEvaluation.EvaluationType;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.engine.context.SlugHelper;
import tech.illuin.wombat.engine.context.asset.InvalidAssetException;
import tech.illuin.wombat.engine.context.asset.UnknownAssetException;
import tech.illuin.wombat.engine.context.environment.UnknownEnvironmentException;
import tech.illuin.wombat.engine.simulation.SimulationAssetConflictException;
import tech.illuin.wombat.engine.simulation.SimulationAssetService;
import tech.illuin.wombat.engine.simulation.SimulationConflictException;
import tech.illuin.wombat.engine.simulation.SimulationPeriod;
import tech.illuin.wombat.engine.simulation.SimulationService;
import tech.illuin.wombat.engine.simulation.UnknownSimulationAssetException;
import tech.illuin.wombat.engine.simulation.UnknownSimulationException;
import tech.illuin.wombat.engine.simulation.evaluation.SimulationEvaluation;
import tech.illuin.wombat.engine.simulation.evaluation.SimulationEvaluator;
import tech.illuin.wombat.engine.simulation.model.SimulationAssetInfo;
import tech.illuin.wombat.engine.simulation.model.SimulationInfo;
import tech.illuin.wombat.engine.simulation.model.SimulationSummary;
import tech.illuin.wombat.engine.simulation.persistence.SimulationAssetEntity;
import tech.illuin.wombat.engine.simulation.persistence.SimulationEntity;
import tech.illuin.wombat.feature.Feature;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static jakarta.ws.rs.core.Response.Status.CONFLICT;

/**
 * The simulation sandbox: simulations of their own, holding asset definitions that are evaluated on demand and never
 * monitored. A sandboxed asset's id is unique within its simulation only, and a copy of a real asset is a snapshot,
 * synced from its source on demand. Unrelated to the stateless {@link SimulationController}, apart from the path.
 */
@Path("api/simulation/sandbox")
@EndpointDisabled(name = Feature.Keys.API_READ, stringValue = "false")
@PermissionsAllowed(Permission.Names.API_SIMULATION_READ)
public class SimulationSandboxController
{
    private final SimulationService service;
    private final SimulationAssetService assetService;
    private final SimulationEvaluator simulationEvaluator;
    private final SecurityIdentity identity;

    public SimulationSandboxController(
        SimulationService service,
        SimulationAssetService assetService,
        SimulationEvaluator simulationEvaluator,
        SecurityIdentity identity
    ) {
        this.service = service;
        this.assetService = assetService;
        this.simulationEvaluator = simulationEvaluator;
        this.identity = identity;
    }

    @GET
        @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", type = SchemaType.ARRAY, implementation = SimulationSummary.class)
    })))
    public Response<List<SimulationSummary>> list()
    {
        Map<String, Long> counts = this.service.assetCounts();
        Map<String, String> environmentNames = this.service.environmentNames();
        return Response.success(
            this.service.list().stream()
                .map(simulation -> SimulationSummary.from(
                    simulation,
                    simulation.sourceEnvironmentId == null ? null : environmentNames.get(simulation.sourceEnvironmentId),
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
    @APIResponse(responseCode = "404", description = "Unknown source environment, or asset in it")
    @APIResponse(responseCode = "409", description = "A simulation with this id already exists")
    public Response<SimulationInfo> create(@NotNull @Valid SimulationCreateRequest request)
    {
        if (!request.assetIds().isEmpty())
            this.requireAssetRead();
        try {
            return Response.success(this.toInfo(this.service.create(
                request.id(), request.name(), request.description(), request.notes(), request.referenceWindow(),
                request.sourceEnvironmentId(), request.assetIds()
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

    @POST
    @Path("/{id}/evaluation")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = SimulationEvaluation.class)
    })))
    @APIResponse(responseCode = "400", description = "Invalid payload")
    @APIResponse(responseCode = "404", description = "Unknown simulation")
    public Response<SimulationEvaluation> evaluate(@PathParam("id") String id, @RequestBody(required = false) @Valid SimulationEvaluationRequest request)
    {
        SimulationEvaluationRequest options = request != null ? request : new SimulationEvaluationRequest(null, null);
        try {
            SimulationEntity simulation = this.service.get(id);
            SimulationPeriod window = options.referenceWindow() != null ? options.referenceWindow() : simulation.referenceWindow;
            return Response.success(this.simulationEvaluator.evaluate(id, this.assetService.list(id), window, options.scopes()));
        }
        catch (UnknownSimulationException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @GET
    @Path("/{id}/assets")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", type = SchemaType.ARRAY, implementation = SimulationAssetInfo.class)
    })))
    @APIResponse(responseCode = "404", description = "Unknown simulation")
    public Response<List<SimulationAssetInfo>> listAssets(@PathParam("id") String id)
    {
        try {
            return Response.success(this.assetService.describe(this.assetService.list(id)));
        }
        catch (UnknownSimulationException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @GET
    @Path("/{id}/assets/{assetId}")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = SimulationAssetInfo.class)
    })))
    @APIResponse(responseCode = "404", description = "Unknown simulation or asset")
    public Response<SimulationAssetInfo> getAsset(@PathParam("id") String id, @PathParam("assetId") String assetId)
    {
        try {
            return Response.success(this.toInfo(this.assetService.get(id, assetId)));
        }
        catch (UnknownSimulationException | UnknownSimulationAssetException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @POST
    @Path("/{id}/assets")
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
    @APIResponse(responseCode = "404", description = "Unknown simulation, or source asset")
    @APIResponse(responseCode = "409", description = "An asset with this id already exists in the simulation")
    public Response<SimulationAssetInfo> createAsset(@PathParam("id") String id, @NotNull @Valid SimulationAssetCreateRequest request)
    {
        if (request.sourceAssetId() != null)
            this.requireAssetRead();
        try {
            return Response.success(this.toInfo(this.assetService.create(
                id, request.id(), request.name(), request.description(), request.notes(),
                request.sourceAssetId(), request.type(), request.properties()
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
    @Path("/{id}/assets/{assetId}")
    @PermissionsAllowed(Permission.Names.API_SIMULATION_WRITE)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = SimulationAssetInfo.class)
    })))
    @APIResponse(responseCode = "400", description = "Invalid payload or properties")
    @APIResponse(responseCode = "404", description = "Unknown simulation or asset")
    public Response<SimulationAssetInfo> updateAsset(
        @PathParam("id") String id,
        @PathParam("assetId") String assetId,
        @NotNull @Valid SimulationAssetUpdateRequest request
    ) {
        try {
            return Response.success(this.toInfo(this.assetService.update(
                id, assetId, request.name(), request.description(), request.notes(), request.properties()
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
    @Path("/{id}/assets/{assetId}")
    @PermissionsAllowed(Permission.Names.API_SIMULATION_WRITE)
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "404", description = "Unknown simulation or asset")
    public void deleteAsset(@PathParam("id") String id, @PathParam("assetId") String assetId)
    {
        try {
            this.assetService.delete(id, assetId);
        }
        catch (UnknownSimulationException | UnknownSimulationAssetException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @POST
    @Path("/{id}/assets/{assetId}/sync")
    @PermissionsAllowed(Permission.Names.API_SIMULATION_WRITE)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = SimulationAssetInfo.class)
    })))
    @APIResponse(responseCode = "403", description = "Syncing also requires " + Permission.Names.API_ASSET_READ)
    @APIResponse(responseCode = "404", description = "Unknown simulation or asset")
    @APIResponse(responseCode = "409", description = "The asset has no source, or it was deleted")
    public Response<SimulationAssetInfo> syncAsset(@PathParam("id") String id, @PathParam("assetId") String assetId)
    {
        this.requireAssetRead();
        try {
            return Response.success(this.toInfo(this.assetService.sync(id, assetId)));
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
            throw new ForbiddenException("Copying assets requires the permission " + Permission.Names.API_ASSET_READ);
    }

    private SimulationInfo toInfo(SimulationEntity entity)
    {
        return SimulationInfo.from(
            entity,
            this.service.sourceEnvironmentName(entity).orElse(null),
            this.assetService.describe(this.assetService.list(entity.id))
        );
    }

    private SimulationAssetInfo toInfo(SimulationAssetEntity entity)
    {
        return this.assetService.describe(List.of(entity)).getFirst();
    }

    /**
     * @param assetIds the assets of {@code sourceEnvironmentId} to copy, none when empty
     */
    public record SimulationCreateRequest(
        @NotBlank @Size(max = SlugHelper.MAX_LENGTH) @Pattern(regexp = SlugHelper.PATTERN, message = SlugHelper.MESSAGE) @JsonProperty("id") @Schema(required = true) String id,
        @NotBlank @Size(max = 255) @JsonProperty("name") @Schema(required = true) String name,
        @Size(max = 4096) @JsonProperty("description") String description,
        @Size(max = 65536) @JsonProperty("notes") String notes,
        @JsonProperty("referenceWindow") @Schema(defaultValue = "MONTH") SimulationPeriod referenceWindow,
        @JsonProperty("sourceEnvironmentId") String sourceEnvironmentId,
        @JsonProperty("assetIds") List<@NotBlank String> assetIds
    ) {
        public SimulationCreateRequest
        {
            assetIds = assetIds != null ? assetIds : List.of();
        }

        @JsonIgnore
        @AssertTrue(message = "assets are copied from a source environment, which is missing")
        public boolean isAssetsWithSource()
        {
            return this.assetIds.isEmpty() || this.sourceEnvironmentId != null;
        }
    }

    /**
     * Replaces the editable fields of a simulation; its id and source environment are immutable.
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

    /**
     * @param scopes          what to evaluate, impact and cost when null
     * @param referenceWindow overrides the simulation's own for this evaluation only
     */
    public record SimulationEvaluationRequest(
        @JsonProperty("scopes") Set<EvaluationType> scopes,
        @JsonProperty("referenceWindow") SimulationPeriod referenceWindow
    ) {
        public SimulationEvaluationRequest
        {
            scopes = scopes != null ? Set.copyOf(scopes) : EnumSet.allOf(EvaluationType.class);
        }
    }

    /**
     * Copies {@code sourceAssetId} when given: its type, and its properties unless {@code properties} are given. The id,
     * name and description of a copy default to its source's. Without a source, {@code id}, {@code name}, {@code type}
     * and {@code properties} are required.
     */
    public record SimulationAssetCreateRequest(
        @Size(max = SlugHelper.MAX_LENGTH) @Pattern(regexp = SlugHelper.PATTERN, message = SlugHelper.MESSAGE) @JsonProperty("id") String id,
        @Size(max = 255) @JsonProperty("name") String name,
        @Size(max = 4096) @JsonProperty("description") String description,
        @Size(max = 65536) @JsonProperty("notes") String notes,
        @JsonProperty("sourceAssetId") String sourceAssetId,
        @JsonProperty("type") String type,
        @JsonProperty("properties") ObjectNode properties
    ) {
        @JsonIgnore
        @AssertTrue(message = "a name is required, unless the asset is copied")
        public boolean isNamed()
        {
            return this.name == null ? this.sourceAssetId != null : !this.name.isBlank();
        }
    }

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
