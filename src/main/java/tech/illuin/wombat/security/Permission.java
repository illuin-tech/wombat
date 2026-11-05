package tech.illuin.wombat.security;

import java.util.Arrays;
import java.util.Optional;

public enum Permission
{
    UI_DASHBOARD_READ(Names.UI_DASHBOARD_READ, false),
    UI_APIKEYS_MANAGE(Names.UI_APIKEYS_MANAGE, false),
    UI_USERS_READ(Names.UI_USERS_READ, false),
    API_IMPACT_READ(Names.API_IMPACT_READ, true),
    API_SIMULATION_READ(Names.API_SIMULATION_READ, true),
    API_ENVIRONMENT_READ(Names.API_ENVIRONMENT_READ, true),
    API_APIKEY_MANAGE(Names.API_APIKEY_MANAGE, false),
    API_USER_MANAGE(Names.API_USER_MANAGE, false),
    API_USER_READ(Names.API_USER_READ, false),
    ;

    private final String value;
    private final boolean grantableToApiKeys;

    Permission(String value, boolean grantableToApiKeys)
    {
        this.value = value;
        this.grantableToApiKeys = grantableToApiKeys;
    }

    public String value()
    {
        return this.value;
    }

    public boolean grantableToApiKeys()
    {
        return this.grantableToApiKeys;
    }

    public static Optional<Permission> fromValue(String value)
    {
        return Arrays.stream(values())
            .filter(permission -> permission.value.equals(value))
            .findFirst();
    }

    public static final class Names
    {
        public static final String UI_DASHBOARD_READ = "ui.dashboard.read";
        public static final String UI_APIKEYS_MANAGE = "ui.apikeys.manage";
        public static final String UI_USERS_READ = "ui.users.read";
        public static final String API_IMPACT_READ = "api.impact.read";
        public static final String API_SIMULATION_READ = "api.simulation.read";
        public static final String API_ENVIRONMENT_READ = "api.environment.read";
        public static final String API_APIKEY_MANAGE = "api.apikey.manage";
        public static final String API_USER_MANAGE = "api.user.manage";
        public static final String API_USER_READ = "api.user.read";

        private Names() {}
    }
}
