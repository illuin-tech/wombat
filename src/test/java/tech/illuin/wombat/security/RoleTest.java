package tech.illuin.wombat.security;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleTest
{
    @Test
    void admin_holdsEveryPermission()
    {
        assertEquals(EnumSet.allOf(Permission.class), Role.ADMIN.permissions());
        assertFalse(Permission.API_USER_READ.grantableToApiKeys());
    }

    @Test
    void users_readTheReportAndManageTheirOwnKeys()
    {
        assertEquals(
            EnumSet.of(
                Permission.UI_DASHBOARD_READ, Permission.UI_APIKEYS_MANAGE, Permission.API_APIKEY_MANAGE,
                Permission.API_IMPACT_READ, Permission.API_SIMULATION_READ, Permission.API_ENVIRONMENT_READ
            ),
            Role.USER.permissions()
        );
    }

    @Test
    void apiKeyManagers_areUsersWhoAlsoManageEveryKey()
    {
        Set<Permission> expected = EnumSet.copyOf(Role.USER.permissions());
        expected.add(Permission.API_APIKEY_ADMIN);
        assertEquals(expected, Role.API_KEY_MANAGER.permissions());
    }

    @Test
    void everyRole_extendsTheUserRole()
    {
        for (Role role : Role.values())
            assertTrue(role.permissions().containsAll(Role.USER.permissions()), role.value());
    }
}
