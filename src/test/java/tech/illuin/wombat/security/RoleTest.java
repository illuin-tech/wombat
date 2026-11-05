package tech.illuin.wombat.security;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleTest
{
    @Test
    void admin_holdsEveryPermission()
    {
        assertEquals(EnumSet.allOf(Permission.class), Role.ADMIN.permissions());
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
