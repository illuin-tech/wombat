package tech.illuin.wombat.endpoint.access;

import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
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
import tech.illuin.wombat.security.user.RoleGrantService;

/** Admin-only: grants and removes Wombat roles held on top of the identity provider's (see the users page). */
@Path("api/users/{userId}/roles/{role}")
@RequiresFeature(Feature.ADMIN)
@PermissionsAllowed(Permission.Names.API_USER_MANAGE)
public class UserRoleController
{
    private final RoleGrantService roleGrants;
    private final SecurityIdentity identity;
    private final AuthProperties properties;

    public UserRoleController(RoleGrantService roleGrants, SecurityIdentity identity, AuthProperties properties)
    {
        this.roleGrants = roleGrants;
        this.identity = identity;
        this.properties = properties;
    }

    @PUT
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
    public Response<Void> grant(@PathParam("userId") String userId, @PathParam("role") String role)
    {
        this.roleGrants.grant(this.caller(), userId, role);
        return Response.status("Granted");
    }

    @DELETE
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
    public Response<Void> revoke(@PathParam("userId") String userId, @PathParam("role") String role)
    {
        this.roleGrants.revoke(this.caller(), userId, role);
        return Response.status("Removed");
    }

    private Caller caller()
    {
        if (!this.properties.enabled())
            throw new NotFoundException();
        return Caller.from(this.identity, this.properties);
    }
}
