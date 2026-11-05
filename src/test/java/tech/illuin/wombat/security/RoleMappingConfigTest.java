package tech.illuin.wombat.security;

import io.quarkus.runtime.configuration.DurationConverter;
import io.smallrye.config.EnvConfigSource;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** auth.roles is a map keyed by role value: deployments set it through env variables, whose names lose the dashes. */
class RoleMappingConfigTest
{
    @Test
    void envVariables_mapEveryRoleIncludingDashedOnes()
    {
        AuthProperties properties = load(new EnvConfigSource(Map.of(
            "AUTH_ROLES_ADMIN", "wombat-admins,chief@example.com",
            "AUTH_ROLES_USER", "*",
            "AUTH_ROLES_API_KEY_MANAGER", "wombat-key-managers"
        ), 300));

        RoleMapping mapping = new RoleMapping(properties);

        assertEquals(Set.of(Role.ADMIN, Role.USER), mapping.map(List.of("wombat-admins"), Optional.empty()));
        assertEquals(Set.of(Role.ADMIN, Role.USER), mapping.map(List.of(), Optional.of("chief@example.com")));
        assertEquals(Set.of(Role.USER, Role.API_KEY_MANAGER), mapping.map(List.of("wombat-key-managers"), Optional.empty()));
    }

    @Test
    void properties_mapRolesByTheirValue()
    {
        AuthProperties properties = load(new PropertiesConfigSource(Map.of("auth.roles.api-key-manager", "wombat-key-managers"), "test", 100));

        RoleMapping mapping = new RoleMapping(properties);

        assertEquals(Set.of(Role.API_KEY_MANAGER), mapping.map(List.of("wombat-key-managers"), Optional.empty()));
    }

    @Test
    void unknownRoleNames_areRejected()
    {
        assertThrows(IllegalStateException.class, () -> RoleMapping.byRole(Map.of("admin", List.of("x"), "api-key-managr", List.of("y"))));
    }

    private static AuthProperties load(io.smallrye.config.common.AbstractConfigSource source)
    {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
            .withMapping(AuthProperties.class)
            .withConverter(Duration.class, 100, new DurationConverter())
            .withSources(source)
            .build();
        return config.getConfigMapping(AuthProperties.class);
    }
}
