package tech.illuin.wombat.endpoint;

import io.quarkus.security.PermissionsAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tech.illuin.wombat.engine.module.registry.schema.AssetSchemaRegistry;
import tech.illuin.wombat.security.Permission;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

@Path("api/modules")
@PermissionsAllowed(Permission.Names.API_MODULE_READ)
public class ModuleController
{
    private final ModuleRegistry registry;
    private final AssetSchemaRegistry schemaRegistry;

    public ModuleController(ModuleRegistry registry, AssetSchemaRegistry schemaRegistry)
    {
        this.registry = registry;
        this.schemaRegistry = schemaRegistry;
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
                    @SchemaProperty(name = "payload", type = SchemaType.ARRAY, implementation = ModuleMetadata.class)
                }
            )
        )
    )
    public Response<List<ModuleMetadata>> list()
    {
        return Response.success(this.registry.listModules());
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
                    @SchemaProperty(name = "payload", type = SchemaType.OBJECT, implementation = ModuleMetadata.class)
                }
            )
        )
    )
    public Response<ModuleMetadata> find(@PathParam("id") String id)
    {
        return this.registry.getModule(id)
            .map(Response::success)
            .orElseThrow(() -> new NotFoundException("No module could be found for id " + id));
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
}
