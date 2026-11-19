package tech.illuin.wombat.endpoint;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import tech.illuin.wombat.engine.context.asset.AssetConflictException;
import tech.illuin.wombat.engine.context.asset.AssetService;
import tech.illuin.wombat.engine.context.asset.InvalidAssetException;
import tech.illuin.wombat.engine.context.asset.UnknownAssetException;
import tech.illuin.wombat.engine.context.asset.model.AssetInfo;
import tech.illuin.wombat.engine.context.environment.UnknownEnvironmentException;
import tech.illuin.wombat.feature.Feature;
import tech.illuin.wombat.security.Permission;

import java.util.List;
import java.util.Optional;

import static jakarta.ws.rs.core.Response.Status.CONFLICT;

/**
 * The assets of every environment. Those declared by the monitored-environments file or a worker are provisioned, kept
 * in sync by the {@link tech.illuin.wombat.engine.context.EnvironmentReconciler} and read-only here; the others are
 * managed through this API. Soft-deleted assets are unknown.
 */
@Path("api/assets")
@EndpointDisabled(name = Feature.Keys.API_READ, stringValue = "false")
@PermissionsAllowed(Permission.Names.API_ASSET_READ)
public class AssetController
{
    private final AssetService service;

    public AssetController(AssetService service)
    {
        this.service = service;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", type = SchemaType.ARRAY, implementation = AssetInfo.class)
    })))
    @APIResponse(responseCode = "404", description = "Unknown environment")
    public Response<List<AssetInfo>> list(@QueryParam("environmentId") String environmentId)
    {
        try {
            return Response.success(
                this.service.list(Optional.ofNullable(environmentId)).stream()
                    .map(AssetInfo::from)
                    .toList()
            );
        }
        catch (UnknownEnvironmentException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = AssetInfo.class)
    })))
    @APIResponse(responseCode = "404", description = "Unknown asset")
    public Response<AssetInfo> get(@PathParam("id") String id)
    {
        try {
            return Response.success(AssetInfo.from(this.service.get(id)));
        }
        catch (UnknownAssetException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @GET
    @Path("/{uuid}/uuid")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = AssetInfo.class)
    })))
    @APIResponse(responseCode = "404", description = "Unknown asset")
    public Response<AssetInfo> getByUuid(@PathParam("uuid") String uuid)
    {
        try {
            return Response.success(AssetInfo.from(this.service.getByUuid(uuid)));
        }
        catch (UnknownAssetException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @POST
    @PermissionsAllowed(Permission.Names.API_ASSET_WRITE)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    @APIResponse(responseCode = "201", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = AssetInfo.class)
    })))
    @APIResponse(responseCode = "400", description = "Invalid payload, unknown asset type or invalid properties")
    @APIResponse(responseCode = "404", description = "Unknown environment")
    @APIResponse(responseCode = "409", description = "An asset with this id already exists")
    public Response<AssetInfo> create(@NotNull @Valid AssetCreateRequest request)
    {
        try {
            return Response.success(AssetInfo.from(this.service.create(
                request.id(), request.environmentId(), request.name(), request.type(), request.description(), request.disabled(), request.properties()
            )));
        }
        catch (UnknownEnvironmentException e) {
            throw new NotFoundException(e.getMessage());
        }
        catch (AssetConflictException e) {
            throw new ClientErrorException(e.getMessage(), CONFLICT);
        }
        catch (InvalidAssetException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @PUT
    @Path("/{id}")
    @PermissionsAllowed(Permission.Names.API_ASSET_WRITE)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = AssetInfo.class)
    })))
    @APIResponse(responseCode = "400", description = "Invalid payload or properties")
    @APIResponse(responseCode = "404", description = "Unknown asset")
    @APIResponse(responseCode = "409", description = "The asset is provisioned")
    public Response<AssetInfo> update(@PathParam("id") String id, @NotNull @Valid AssetUpdateRequest request)
    {
        try {
            return Response.success(AssetInfo.from(this.service.update(id, request.name(), request.description(), request.disabled(), request.properties())));
        }
        catch (UnknownAssetException e) {
            throw new NotFoundException(e.getMessage());
        }
        catch (AssetConflictException e) {
            throw new ClientErrorException(e.getMessage(), CONFLICT);
        }
        catch (InvalidAssetException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @DELETE
    @Path("/{id}")
    @PermissionsAllowed(Permission.Names.API_ASSET_WRITE)
    @APIResponse(responseCode = "204", description = "Soft-deleted")
    @APIResponse(responseCode = "404", description = "Unknown asset")
    @APIResponse(responseCode = "409", description = "The asset is provisioned")
    public void delete(@PathParam("id") String id)
    {
        try {
            this.service.delete(id);
        }
        catch (UnknownAssetException e) {
            throw new NotFoundException(e.getMessage());
        }
        catch (AssetConflictException e) {
            throw new ClientErrorException(e.getMessage(), CONFLICT);
        }
    }

    /**
     * The identity of the asset, and its type-specific {@code properties} (e.g. its {@code profile}) as they would be
     * declared in the monitored-environments file, in JSON.
     */
    public record AssetCreateRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = SlugHelper.PATTERN, message = SlugHelper.MESSAGE) @JsonProperty("id") @Schema(required = true) String id,
        @NotBlank @JsonProperty("environmentId") @Schema(required = true) String environmentId,
        @NotBlank @Size(max = 255) @JsonProperty("name") @Schema(required = true) String name,
        @NotBlank @JsonProperty("type") @Schema(required = true) String type,
        @Size(max = 4096) @JsonProperty("description") String description,
        @JsonProperty("disabled") boolean disabled,
        @NotNull @JsonProperty("properties") @Schema(required = true) ObjectNode properties
    ) {}

    /**
     * Replaces the editable fields of an asset; its id, environment and type are immutable.
     */
    public record AssetUpdateRequest(
        @NotBlank @Size(max = 255) @JsonProperty("name") @Schema(required = true) String name,
        @Size(max = 4096) @JsonProperty("description") String description,
        @JsonProperty("disabled") boolean disabled,
        @NotNull @JsonProperty("properties") @Schema(required = true) ObjectNode properties
    ) {}
}
