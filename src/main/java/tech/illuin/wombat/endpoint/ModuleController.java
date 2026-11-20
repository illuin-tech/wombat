package tech.illuin.wombat.endpoint;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.resteasy.reactive.server.EndpointDisabled;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.engine.module.ModuleInfo;
import tech.illuin.wombat.engine.module.ModuleService;
import tech.illuin.wombat.engine.module.registry.ModuleConflictException;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tech.illuin.wombat.engine.module.registry.UnknownModuleException;
import tech.illuin.wombat.engine.module.registry.schema.AssetSchemaRegistry;
import tech.illuin.wombat.feature.Feature;
import tech.illuin.wombat.security.Permission;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

import static jakarta.ws.rs.core.Response.Status.CONFLICT;

@Path("api/modules")
@EndpointDisabled(name = Feature.Keys.API_READ, stringValue = "false")
@PermissionsAllowed(Permission.Names.API_MODULE_READ)
public class ModuleController
{
    private final ModuleService service;
    private final ModuleRegistry registry;
    private final AssetSchemaRegistry schemaRegistry;
    private final SecurityIdentity identity;

    public ModuleController(ModuleService service, ModuleRegistry registry, AssetSchemaRegistry schemaRegistry, SecurityIdentity identity)
    {
        this.service = service;
        this.registry = registry;
        this.schemaRegistry = schemaRegistry;
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
                    @SchemaProperty(name = "payload", type = SchemaType.ARRAY, implementation = ModuleInfo.class)
                }
            )
        )
    )
    public Response<List<ModuleInfo>> list()
    {
        return Response.success(this.service.list());
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(
        responseCode = "200",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                properties = {
                    @SchemaProperty(name = "status", implementation = Status.class),
                    @SchemaProperty(name = "payload", type = SchemaType.OBJECT, implementation = ModuleInfo.class)
                }
            )
        )
    )
    @APIResponse(responseCode = "404", description = "Unknown module")
    public Response<ModuleInfo> find(@PathParam("id") String id)
    {
        try {
            return Response.success(this.service.find(id));
        }
        catch (UnknownModuleException e) {
            throw new NotFoundException(e.getMessage());
        }
    }

    @GET
    @Path("/{id}/schema")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(
        responseCode = "200",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                properties = {
                    @SchemaProperty(name = "status", implementation = Status.class),
                    @SchemaProperty(name = "payload", type = SchemaType.OBJECT, implementation = JsonNode.class)
                }
            )
        )
    )
    public Response<JsonNode> getSchema(@PathParam("id") String id)
    {
        return this.registry.getModule(id)
            .flatMap(m -> this.schemaRegistry.getSchema(m.type()))
            .map(Response::success)
            .orElseThrow(() -> new NotFoundException("No module could be found for id " + id));
    }

    @PUT
    @Path("/{id}")
    @PermissionsAllowed(Permission.Names.API_MODULE_MANAGE)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = ModuleInfo.class)
    })))
    @APIResponse(responseCode = "400", description = "Invalid payload")
    @APIResponse(responseCode = "404", description = "Unknown module")
    @APIResponse(responseCode = "409", description = "The module is missing")
    public Response<ModuleInfo> update(@PathParam("id") String id, @NotNull @Valid ModuleUpdateRequest request)
    {
        try {
            return Response.success(this.service.setEnabled(id, request.enabled(), this.user()));
        }
        catch (UnknownModuleException e) {
            throw new NotFoundException(e.getMessage());
        }
        catch (ModuleConflictException e) {
            throw new ClientErrorException(e.getMessage(), CONFLICT);
        }
    }

    @DELETE
    @Path("/{id}")
    @PermissionsAllowed(Permission.Names.API_MODULE_MANAGE)
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "404", description = "Unknown module")
    @APIResponse(responseCode = "409", description = "The module is detected, only a missing one can be deleted")
    public void delete(@PathParam("id") String id)
    {
        try {
            this.service.remove(id);
        }
        catch (UnknownModuleException e) {
            throw new NotFoundException(e.getMessage());
        }
        catch (ModuleConflictException e) {
            throw new ClientErrorException(e.getMessage(), CONFLICT);
        }
    }

    /* Null when authentication is disabled, every caller is then anonymous */
    private String user()
    {
        return this.identity.isAnonymous() ? null : this.identity.getPrincipal().getName();
    }

    public record ModuleUpdateRequest(
        @NotNull @JsonProperty("enabled") @Schema(required = true) Boolean enabled
    ) {}
}
