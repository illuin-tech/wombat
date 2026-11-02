package tech.illuin.wombat.endpoint.access;

import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.security.AuthProperties;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.user.RoleGrantService;

/** Admin-only: grants and removes Wombat roles held on top of the identity provider's (see the users page). */
@Path("api/users/{userId}/roles/{role}")
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
    public Response<Void> grant(@PathParam("userId") String userId, @PathParam("role") String role)
    {
        this.roleGrants.grant(this.caller(), userId, role);
        return Response.status("Granted");
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
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
