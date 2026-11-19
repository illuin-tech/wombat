package tech.illuin.wombat.endpoint;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.resteasy.reactive.server.EndpointDisabled;
import io.quarkus.security.PermissionsAllowed;
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
import tech.illuin.wombat.engine.context.asset.model.AssetHistoryInfo;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConfigHistoryRepository;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.environment.EnvironmentConflictException;
import tech.illuin.wombat.engine.context.environment.EnvironmentService;
import tech.illuin.wombat.engine.context.environment.UnknownEnvironmentException;
import tech.illuin.wombat.engine.context.environment.model.EnvironmentInfo;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentEntity;
import tech.illuin.wombat.feature.Feature;
import tech.illuin.wombat.security.Permission;

import java.util.List;

import static jakarta.ws.rs.core.Response.Status.CONFLICT;

/**
 * The environments, with their assets and the per-asset change history. Those declared by the monitored-environments
 * file or a worker are provisioned, kept in sync by the {@link tech.illuin.wombat.engine.context.EnvironmentReconciler}
 * and read-only here; the others are managed through this API. Soft-deleted environments are unknown.
 */
@Path("api/environments")
@EndpointDisabled(name = Feature.Keys.API_READ, stringValue = "false")
@PermissionsAllowed(Permission.Names.API_ENVIRONMENT_READ)
public class EnvironmentController
{
    private final EnvironmentService service;
    private final AssetRepository assetRepository;
    private final AssetConfigHistoryRepository historyRepository;

    public EnvironmentController(EnvironmentService service, AssetRepository assetRepository, AssetConfigHistoryRepository historyRepository)
    {
        this.service = service;
        this.assetRepository = assetRepository;
        this.historyRepository = historyRepository;
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
                    @SchemaProperty(name = "payload", type = SchemaType.ARRAY, implementation = EnvironmentInfo.class)
                }
            )
        )
    )
    public Response<List<EnvironmentInfo>> list()
    {
        return Response.success(
            this.service.list().stream()
                .map(this::toInfo)
                .toList()
        );
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = EnvironmentInfo.class)
    })))
    @APIResponse(responseCode = "404", description = "Unknown environment")
    public Response<EnvironmentInfo> get(@PathParam("id") String id)
    {
        try {
            return Response.success(this.toInfo(this.service.get(id)));
        }
        catch (UnknownEnvironmentException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @GET
    @Path("/{uuid}/uuid")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = EnvironmentInfo.class)
    })))
    @APIResponse(responseCode = "404", description = "Unknown environment")
    public Response<EnvironmentInfo> getByUuid(@PathParam("uuid") String uuid)
    {
        try {
            return Response.success(this.toInfo(this.service.getByUuid(uuid)));
        }
        catch (UnknownEnvironmentException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @POST
    @PermissionsAllowed(Permission.Names.API_ENVIRONMENT_WRITE)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    @APIResponse(responseCode = "201", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = EnvironmentInfo.class)
    })))
    @APIResponse(responseCode = "400", description = "Invalid payload")
    @APIResponse(responseCode = "409", description = "An environment with this id already exists")
    public Response<EnvironmentInfo> create(@NotNull @Valid EnvironmentCreateRequest request)
    {
        try {
            return Response.success(this.toInfo(this.service.create(request.id(), request.name(), request.description(), request.disabled())));
        }
        catch (EnvironmentConflictException e) {
            throw new ClientErrorException(e.getMessage(), CONFLICT);
        }
    }

    @PUT
    @Path("/{id}")
    @PermissionsAllowed(Permission.Names.API_ENVIRONMENT_WRITE)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = EnvironmentInfo.class)
    })))
    @APIResponse(responseCode = "400", description = "Invalid payload")
    @APIResponse(responseCode = "404", description = "Unknown environment")
    @APIResponse(responseCode = "409", description = "The environment is provisioned")
    public Response<EnvironmentInfo> update(@PathParam("id") String id, @NotNull @Valid EnvironmentUpdateRequest request)
    {
        try {
            return Response.success(this.toInfo(this.service.update(id, request.name(), request.description(), request.disabled())));
        }
        catch (UnknownEnvironmentException e) {
            throw new NotFoundException(e.getMessage());
        }
        catch (EnvironmentConflictException e) {
            throw new ClientErrorException(e.getMessage(), CONFLICT);
        }
    }

    @DELETE
    @Path("/{id}")
    @PermissionsAllowed(Permission.Names.API_ENVIRONMENT_WRITE)
    @APIResponse(responseCode = "204", description = "Soft-deleted")
    @APIResponse(responseCode = "404", description = "Unknown environment")
    @APIResponse(responseCode = "409", description = "The environment is provisioned, or still holds assets")
    public void delete(@PathParam("id") String id)
    {
        try {
            this.service.delete(id);
        }
        catch (UnknownEnvironmentException e) {
            throw new NotFoundException(e.getMessage());
        }
        catch (EnvironmentConflictException e) {
            throw new ClientErrorException(e.getMessage(), CONFLICT);
        }
    }

    @GET
    @Path("/{id}/assets/{assetId}/history")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(
        responseCode = "200",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                properties = {
                    @SchemaProperty(name = "status", implementation = Status.class),
                    @SchemaProperty(name = "payload", type = SchemaType.ARRAY, implementation = AssetHistoryInfo.class)
                }
            )
        )
    )
    public Response<List<AssetHistoryInfo>> history(@PathParam("id") String id, @PathParam("assetId") String assetId)
    {
        try {
            this.service.get(id);
            return Response.success(
                this.historyRepository.findByAsset(assetId).stream()
                    .map(AssetHistoryInfo::from)
                    .toList()
            );
        }
        catch (UnknownEnvironmentException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    private EnvironmentInfo toInfo(EnvironmentEntity entity)
    {
        return EnvironmentInfo.from(entity, this.assetRepository.findVisibleByEnvironment(entity.id));
    }

    public record EnvironmentCreateRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = SlugHelper.PATTERN, message = SlugHelper.MESSAGE) @JsonProperty("id") @Schema(required = true) String id,
        @NotBlank @Size(max = 255) @JsonProperty("name") @Schema(required = true) String name,
        @Size(max = 4096) @JsonProperty("description") String description,
        @JsonProperty("disabled") boolean disabled
    ) {}

    /**
     * Replaces the editable fields of an environment; its id is immutable.
     */
    public record EnvironmentUpdateRequest(
        @NotBlank @Size(max = 255) @JsonProperty("name") @Schema(required = true) String name,
        @Size(max = 4096) @JsonProperty("description") String description,
        @JsonProperty("disabled") boolean disabled
    ) {}
}
