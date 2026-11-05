package tech.illuin.wombat.endpoint.access;

import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.security.AuthProperties;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.Role;
import tech.illuin.wombat.security.user.RoleGrantEntity;
import tech.illuin.wombat.security.user.RoleGrantService;
import tech.illuin.wombat.security.user.UserEntity;
import tech.illuin.wombat.security.user.UserService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("api/users")
@PermissionsAllowed(Permission.Names.API_USER_READ)
public class UserController
{
    private final UserService userService;
    private final RoleGrantService roleGrants;
    private final SecurityIdentity identity;
    private final AuthProperties properties;

    public UserController(UserService userService, RoleGrantService roleGrants, SecurityIdentity identity, AuthProperties properties)
    {
        this.userService = userService;
        this.roleGrants = roleGrants;
        this.identity = identity;
        this.properties = properties;
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
                    @SchemaProperty(name = "payload", type = SchemaType.ARRAY, implementation = UserInfo.class)
                }
            )
        )
    )
    public Response<List<UserInfo>> list()
    {
        if (!this.properties.enabled())
            throw new NotFoundException();

        Caller caller = Caller.from(this.identity, this.properties);
        Map<String, Map<Role, RoleGrantEntity>> grants = this.roleGrants.byUser();
        List<UserEntity> users = this.userService.list();
        Map<String, String> names = users.stream().collect(Collectors.toMap(user -> user.id, UserController::displayName));
        List<UserInfo> views = users.stream()
            .map(user -> view(user, grants.getOrDefault(user.id, Map.of()), names, caller))
            .toList();
        return Response.success(views);
    }

    private static UserInfo view(UserEntity user, Map<Role, RoleGrantEntity> granted, Map<String, String> names, Caller caller)
    {
        boolean self = user.issuer.equals(caller.issuer()) && user.subject.equals(caller.subject());
        boolean canManage = caller.has(Permission.API_USER_MANAGE);
        List<UserInfo.RoleInfo> roles = Arrays.stream(Role.values())
            .filter(role -> user.roles.contains(role) || granted.containsKey(role))
            .map(role -> {
                RoleGrantEntity grant = granted.get(role);
                boolean fromProvider = user.roles.contains(role);
                UserInfo.Grant grantInfo = grant == null ? null : new UserInfo.Grant(
                    names.getOrDefault(grant.grantedBy, grant.grantedBy),
                    grant.grantedAt
                );
                boolean removable = canManage && grant != null && !(self && role == Role.ADMIN);
                return new UserInfo.RoleInfo(role.value(), fromProvider, grantInfo, removable);
            })
            .toList();
        List<String> grantable = canManage
            ? Arrays.stream(Role.values())
                .filter(role -> !user.roles.contains(role) && !granted.containsKey(role))
                .map(Role::value)
                .toList()
            : List.of();

        return new UserInfo(
            user.id,
            displayName(user),
            user.email,
            self,
            user.createdAt,
            user.lastSeenAt,
            roles,
            grantable
        );
    }

    private static String displayName(UserEntity user)
    {
        return user.name != null ? user.name : user.subject;
    }
}
