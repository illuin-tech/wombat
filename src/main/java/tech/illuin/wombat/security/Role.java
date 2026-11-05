package tech.illuin.wombat.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static tech.illuin.wombat.security.Permission.*;

public enum Role
{
    ADMIN("admin", EnumSet.allOf(Permission.class)),
    USER("user", Base.PERMISSIONS),
    API_KEY_MANAGER("api-key-manager", Base.with(API_APIKEY_ADMIN)),
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

    /* Every role extends the user role: it reads the report and manages its own API keys. A holder class, since an enum
       constant cannot read another one while the enum is being initialized. */
    private static final class Base
    {
        static final Set<Permission> PERMISSIONS = Collections.unmodifiableSet(EnumSet.of(
            UI_DASHBOARD_READ,
            UI_APIKEYS_MANAGE,
            API_APIKEY_MANAGE,
            API_IMPACT_READ,
            API_SIMULATION_READ,
            API_ENVIRONMENT_READ
        ));

        static Set<Permission> with(Permission... extra)
        {
            Set<Permission> permissions = EnumSet.copyOf(PERMISSIONS);
            permissions.addAll(Arrays.asList(extra));
            return Collections.unmodifiableSet(permissions);
        }
    }

    public static Set<Permission> permissionsOf(Collection<Role> roles)
    {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        roles.forEach(role -> permissions.addAll(role.permissions));
        return permissions;
    }
}
