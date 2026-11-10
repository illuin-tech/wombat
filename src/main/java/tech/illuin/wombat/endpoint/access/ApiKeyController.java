package tech.illuin.wombat.endpoint.access;

import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.feature.Feature;
import tech.illuin.wombat.feature.RequiresFeature;
import tech.illuin.wombat.security.AuthProperties;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.apikey.ApiKeyService;

import java.util.List;

/** Users manage their own API keys; admins also list ({@code ?all=true}) and revoke everyone's. */
@Path("api/keys")
@RequiresFeature(Feature.ADMIN)
@PermissionsAllowed(Permission.Names.API_APIKEY_MANAGE)
public class ApiKeyController
{
    private final ApiKeyService service;
    private final SecurityIdentity identity;
    private final AuthProperties properties;

    public ApiKeyController(ApiKeyService service, SecurityIdentity identity, AuthProperties properties)
    {
        this.service = service;
        this.identity = identity;
        this.properties = properties;
    }

    @GET
    @Path("settings")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(
        responseCode = "200",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                properties = {
                    @SchemaProperty(name = "status", implementation = Status.class),
                    @SchemaProperty(name = "payload", implementation = ApiKeySettings.class)
                }
            )
        )
    )
    public Response<ApiKeySettings> settings()
    {
        this.requireAuthentication();
        List<String> grantable = Caller.permissions(this.identity).stream()
            .filter(Permission::grantableToApiKeys)
            .map(Permission::value)
            .sorted()
            .toList();
        return Response.success(new ApiKeySettings(
            grantable,
            this.properties.apiKeys().maxLifetime().toDays(),
            this.properties.apiKeys().allowNeverExpiring()
        ));
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
                    @SchemaProperty(name = "payload", type = SchemaType.ARRAY, implementation = ApiKeyInfo.class)
                }
            )
        )
    )
    public Response<List<ApiKeyInfo>> list(@QueryParam("all") @DefaultValue("false") boolean all)
    {
        this.requireAuthentication();
        return Response.success(this.service.list(this.caller(), all).stream().map(ApiKeyInfo::from).toList());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(
        responseCode = "200",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                properties = {
                    @SchemaProperty(name = "status", implementation = Status.class),
                    @SchemaProperty(name = "payload", implementation = ApiKeyInfo.class)
                }
            )
        )
    )
    public Response<ApiKeyInfo> create(ApiKeyRequest request)
    {
        if (request == null)
            throw new BadRequestException("Missing API key request");

        this.requireAuthentication();
        Caller caller = this.caller();
        ApiKeyService.Issued issued = this.service.create(caller, request.name(), request.permissions(), request.expiresAt());
        return Response.success("The token is only shown once", ApiKeyInfo.from(issued.key(), caller.name(), issued.token()));
    }

    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(
        responseCode = "200",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                properties = {
                    @SchemaProperty(name = "status", implementation = Status.class)
                }
            )
        )
    )
    public Response<Void> revoke(@PathParam("id") String id)
    {
        this.requireAuthentication();
        this.service.revoke(this.caller(), id);
        return Response.status("Revoked");
    }

    private Caller caller()
    {
        return Caller.from(this.identity, this.properties);
    }

    private void requireAuthentication()
    {
        if (!this.properties.enabled())
            throw new NotFoundException();
    }
}
