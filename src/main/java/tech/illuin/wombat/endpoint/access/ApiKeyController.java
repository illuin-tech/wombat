package tech.illuin.wombat.endpoint.access;

import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.security.AuthProperties;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.apikey.ApiKeyService;

import java.util.List;

/** Admin-only: API keys belong to no one, so whoever may manage keys sees and manages all of them. */
@Path("api/keys")
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
    @Produces(MediaType.APPLICATION_JSON)
    public Response<List<ApiKeyInfo>> list()
    {
        this.requireAuthentication();
        return Response.success(this.service.list().stream().map(ApiKeyInfo::from).toList());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response<ApiKeyInfo> create(ApiKeyRequest request)
    {
        if (request == null)
            throw new BadRequestException("Missing API key request");

        this.requireAuthentication();
        Caller caller = Caller.from(this.identity, this.properties);
        ApiKeyService.Issued issued = this.service.create(caller, request.name(), request.permissions(), request.expiresAt());
        return Response.success("The token is only shown once", ApiKeyInfo.from(issued.key(), caller.name(), issued.token()));
    }

    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response<Void> revoke(@PathParam("id") String id)
    {
        this.requireAuthentication();
        this.service.revoke(id);
        return Response.status("Revoked");
    }

    private void requireAuthentication()
    {
        if (!this.properties.enabled())
            throw new NotFoundException();
    }
}
