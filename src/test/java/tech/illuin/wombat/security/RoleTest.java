package tech.illuin.wombat.security;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RoleTest
{
    @Test
    void admin_holdsEveryPermission()
    {
        assertEquals(EnumSet.allOf(Permission.class), Role.ADMIN.permissions());
        assertFalse(Permission.API_USER_READ.grantableToApiKeys());
    }

    @Test
    void users_onlyReadTheReport()
    {
        assertEquals(
            EnumSet.of(Permission.UI_DASHBOARD_READ, Permission.API_IMPACT_READ, Permission.API_SIMULATION_READ, Permission.API_ENVIRONMENT_READ),
            Role.USER.permissions()
        );
    }
}
