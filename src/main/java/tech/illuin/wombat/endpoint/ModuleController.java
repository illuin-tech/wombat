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
import org.jboss.resteasy.reactive.ResponseStatus;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.engine.module.DeploymentInfo;
import tech.illuin.wombat.engine.module.ModuleInfo;
import tech.illuin.wombat.engine.module.ModulePrecedence;
import tech.illuin.wombat.engine.module.ModuleService;
import tech.illuin.wombat.engine.module.registry.ModuleConflictException;
import tech.illuin.wombat.engine.module.registry.ModuleOrderException;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tech.illuin.wombat.engine.module.registry.UnknownModuleException;
import tech.illuin.wombat.engine.module.runtime.DeploymentConflictException;
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

    /**
     * Sets the order of the modules, which the family modules of a family are tried in; the core follows it once
     * redeployed.
     */
    @PUT
    @Path("/order")
    @PermissionsAllowed(Permission.Names.API_MODULE_MANAGE)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", type = SchemaType.ARRAY, implementation = ModuleInfo.class)
    })))
    @APIResponse(responseCode = "400", description = "The order does not list each registered module exactly once")
    public Response<List<ModuleInfo>> setOrder(@NotNull @Valid ModuleOrderRequest request)
    {
        try {
            return Response.success(this.service.setOrder(request.modules(), this.user()));
        }
        catch (ModuleOrderException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    /**
     * Which family module serves first, for each family and component, in the latest deployment and in the next one.
     */
    @GET
    @Path("/precedence")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = ModulePrecedence.class)
    })))
    public Response<ModulePrecedence> precedence()
    {
        return Response.success(this.service.precedence());
    }

    @GET
    @Path("/deployment")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = DeploymentInfo.class)
    })))
    public Response<DeploymentInfo> deployment()
    {
        return Response.success(this.service.deployment());
    }

    /**
     * Records a deployment of the modules enabled now, and deploys it on this instance; the others follow on their next
     * poll. A build failing here is reported in the payload, the running core being kept.
     */
    @POST
    @Path("/deployment")
    @PermissionsAllowed(Permission.Names.API_MODULE_MANAGE)
    @Produces(MediaType.APPLICATION_JSON)
    @ResponseStatus(201)
    @APIResponse(responseCode = "201", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = DeploymentInfo.Redeployed.class)
    })))
    @APIResponse(responseCode = "409", description = "Another redeploy was requested at the same time")
    public Response<DeploymentInfo.Redeployed> redeploy()
    {
        try {
            return Response.success(this.service.redeploy(this.user()));
        }
        catch (DeploymentConflictException e) {
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

    /**
     * @param modules every registered module, disabled and missing ones included
     */
    public record ModuleOrderRequest(
        @NotNull @JsonProperty("modules") @Schema(required = true) List<String> modules
    ) {}
}
