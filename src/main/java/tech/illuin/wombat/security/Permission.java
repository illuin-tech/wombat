package tech.illuin.wombat.security;

import java.util.Arrays;
import java.util.Optional;

public enum Permission
{
    UI_DASHBOARD_READ(Names.UI_DASHBOARD_READ, false),
    UI_APIKEYS_MANAGE(Names.UI_APIKEYS_MANAGE, false),
    UI_USERS_READ(Names.UI_USERS_READ, false),
    UI_ENVIRONMENTS_READ(Names.UI_ENVIRONMENTS_READ, false),
    UI_ENVIRONMENTS_MANAGE(Names.UI_ENVIRONMENTS_MANAGE, false),
    UI_MODULES_READ(Names.UI_MODULES_READ, false),
    UI_MODULES_MANAGE(Names.UI_MODULES_MANAGE, false),
    UI_SIMULATIONS_READ(Names.UI_SIMULATIONS_READ, false),
    UI_SIMULATIONS_MANAGE(Names.UI_SIMULATIONS_MANAGE, false),
    API_IMPACT_READ(Names.API_IMPACT_READ, true),
    API_SIMULATION_READ(Names.API_SIMULATION_READ, true),
    API_SIMULATION_WRITE(Names.API_SIMULATION_WRITE, true),
    API_ENVIRONMENT_READ(Names.API_ENVIRONMENT_READ, true),
    API_ENVIRONMENT_WRITE(Names.API_ENVIRONMENT_WRITE, true),
    API_ASSET_READ(Names.API_ASSET_READ, true),
    API_ASSET_WRITE(Names.API_ASSET_WRITE, true),
    API_METRIC_WRITE(Names.API_METRIC_WRITE, true),
    API_MODULE_READ(Names.API_MODULE_READ, true),
    API_MODULE_MANAGE(Names.API_MODULE_MANAGE, true),
    API_APIKEY_MANAGE(Names.API_APIKEY_MANAGE, false),
    API_APIKEY_ADMIN(Names.API_APIKEY_ADMIN, false),
    API_USER_MANAGE(Names.API_USER_MANAGE, false),
    API_USER_READ(Names.API_USER_READ, false),
    API_SECRET_READ(Names.API_SECRET_READ, false),
    API_SECRET_MANAGE(Names.API_SECRET_MANAGE, false),
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
        public static final String UI_ENVIRONMENTS_READ = "ui.environments.read";
        public static final String UI_ENVIRONMENTS_MANAGE = "ui.environments.manage";
        public static final String UI_MODULES_READ = "ui.modules.read";
        public static final String UI_MODULES_MANAGE = "ui.modules.manage";
        public static final String UI_SIMULATIONS_READ = "ui.simulations.read";
        public static final String UI_SIMULATIONS_MANAGE = "ui.simulations.manage";
        public static final String API_IMPACT_READ = "api.impact.read";
        public static final String API_SIMULATION_READ = "api.simulation.read";
        public static final String API_SIMULATION_WRITE = "api.simulation.write";
        public static final String API_MODULE_READ = "api.module.read";
        public static final String API_MODULE_MANAGE = "api.module.manage";
        public static final String API_ENVIRONMENT_READ = "api.environment.read";
        public static final String API_ENVIRONMENT_WRITE = "api.environment.write";
        public static final String API_ASSET_READ = "api.asset.read";
        public static final String API_ASSET_WRITE = "api.asset.write";
        public static final String API_METRIC_WRITE = "api.metric.write";
        public static final String API_APIKEY_MANAGE = "api.apikey.manage";
        public static final String API_APIKEY_ADMIN = "api.apikey.admin";
        public static final String API_USER_MANAGE = "api.user.manage";
        public static final String API_USER_READ = "api.user.read";
        public static final String API_SECRET_READ = "api.secret.read";
        public static final String API_SECRET_MANAGE = "api.secret.manage";

        private Names() {}
    }
}
