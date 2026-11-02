package tech.illuin.wombat.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static tech.illuin.wombat.security.Permission.*;

public enum Role
{
    ADMIN("admin", EnumSet.allOf(Permission.class)),
    USER("user", EnumSet.of(
        UI_DASHBOARD_READ,
        API_IMPACT_READ,
        API_SIMULATION_READ,
        API_ENVIRONMENT_READ
    )),
    ;

    private final String value;
    private final Set<Permission> permissions;

    Role(String value, Set<Permission> permissions)
    {
        this.value = value;
        this.permissions = permissions;
    }

    public String value()
    {
        return this.value;
    }

    public Set<Permission> permissions()
    {
        return this.permissions;
    }

    public static Optional<Role> fromValue(String value)
    {
        return Arrays.stream(values())
            .filter(role -> role.value.equals(value))
            .findFirst();
    }

    public static Set<Permission> permissionsOf(Collection<Role> roles)
    {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        roles.forEach(role -> permissions.addAll(role.permissions));
        return permissions;
    }
}
