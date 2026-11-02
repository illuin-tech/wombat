package tech.illuin.wombat.ui;

import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import tech.illuin.wombat.security.AuthProperties;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.Role;
import tech.illuin.wombat.security.user.RoleGrantEntity;
import tech.illuin.wombat.security.user.RoleGrantService;
import tech.illuin.wombat.security.user.UserEntity;
import tech.illuin.wombat.security.user.UserService;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Admin page listing every user who signed in, with their roles and where each comes from, and granting roles in Wombat. */
@Path("users")
@PermissionsAllowed(Permission.Names.UI_USERS_READ)
public class UsersPageController
{
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'", Locale.US).withZone(ZoneOffset.UTC);

    private final UserService userService;
    private final RoleGrantService roleGrants;
    private final SecurityIdentity identity;
    private final AuthProperties properties;

    public UsersPageController(UserService userService, RoleGrantService roleGrants, SecurityIdentity identity, AuthProperties properties)
    {
        this.userService = userService;
        this.roleGrants = roleGrants;
        this.identity = identity;
        this.properties = properties;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance get()
    {
        if (!this.properties.enabled())
            return Templates.users(new Templates.UsersPage(false, false, List.of()));

        Caller caller = Caller.from(this.identity, this.properties);
        boolean canManage = caller.has(Permission.API_USER_MANAGE);
        Map<String, Map<Role, RoleGrantEntity>> grants = this.roleGrants.byUser();
        List<UserEntity> users = this.userService.list();
        Map<String, String> names = users.stream().collect(Collectors.toMap(user -> user.id, UsersPageController::displayName));

        List<Templates.UserView> views = users.stream()
            .map(user -> view(user, grants.getOrDefault(user.id, Map.of()), names, caller, canManage))
            .toList();
        return Templates.users(new Templates.UsersPage(true, canManage, views));
    }

    private static Templates.UserView view(UserEntity user, Map<Role, RoleGrantEntity> granted, Map<String, String> names, Caller caller, boolean canManage)
    {
        boolean self = user.issuer.equals(caller.issuer()) && user.subject.equals(caller.subject());
        List<Templates.RoleView> roles = Arrays.stream(Role.values())
            .filter(role -> user.roles.contains(role) || granted.containsKey(role))
            .map(role -> {
                RoleGrantEntity grant = granted.get(role);
                boolean fromProvider = user.roles.contains(role);
                String source = grant == null
                    ? "From the identity provider or auth.roles configuration"
                    : "Granted in Wombat by " + names.getOrDefault(grant.grantedBy, grant.grantedBy) + " on " + format(grant.grantedAt)
                        + (fromProvider ? ", also from the identity provider or configuration" : "");
                boolean removable = canManage && grant != null && !(self && role == Role.ADMIN);
                return new Templates.RoleView(role.value(), grant != null, removable, source);
            })
            .toList();
        List<String> grantable = canManage
            ? Arrays.stream(Role.values()).filter(role -> !user.roles.contains(role) && !granted.containsKey(role)).map(Role::value).toList()
            : List.of();

        return new Templates.UserView(
            user.id,
            displayName(user),
            user.email,
            self,
            roles,
            grantable,
            format(user.createdAt),
            format(user.lastSeenAt)
        );
    }

    private static String displayName(UserEntity user)
    {
        return user.name != null ? user.name : user.subject;
    }

    private static String format(Instant instant)
    {
        return instant == null ? "—" : DATE.format(instant);
    }
}
