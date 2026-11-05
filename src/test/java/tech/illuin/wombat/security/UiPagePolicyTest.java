package tech.illuin.wombat.security;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;

import java.security.Permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiPagePolicyTest
{
    @Test
    void requiredFor_mapsSpaPathsToTheirPermissions()
    {
        assertEquals(tech.illuin.wombat.security.Permission.UI_DASHBOARD_READ, UiPagePolicy.requiredFor("/"));
        assertEquals(tech.illuin.wombat.security.Permission.UI_DASHBOARD_READ, UiPagePolicy.requiredFor("/impact/test"));
        assertEquals(tech.illuin.wombat.security.Permission.UI_USERS_READ, UiPagePolicy.requiredFor("/users"));
        assertEquals(tech.illuin.wombat.security.Permission.UI_USERS_READ, UiPagePolicy.requiredFor("/users/123"));
        assertEquals(tech.illuin.wombat.security.Permission.UI_APIKEYS_MANAGE, UiPagePolicy.requiredFor("/api-keys"));
        assertEquals(tech.illuin.wombat.security.Permission.UI_APIKEYS_MANAGE, UiPagePolicy.requiredFor("/api-keys/settings"));
    }

    @Test
    void checkPermission_appliesAccessPolicyBeforeUiPermission()
    {
        AuthProperties properties = mock(AuthProperties.class);
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        SecurityIdentity identity = mock(SecurityIdentity.class);
        RoutingContext request = mock(RoutingContext.class);
        HttpSecurityPolicy.AuthorizationRequestContext requestContext = mock(HttpSecurityPolicy.AuthorizationRequestContext.class);
        when(properties.enabled()).thenReturn(true);
        when(request.normalizedPath()).thenReturn("/users");
        when(accessPolicy.check(identity)).thenReturn(HttpSecurityPolicy.CheckResult.DENY);

        UiPagePolicy policy = new UiPagePolicy(accessPolicy, properties);
        HttpSecurityPolicy.CheckResult result = policy.checkPermission(
            request,
            Uni.createFrom().item(identity),
            requestContext
        ).await().indefinitely();

        assertEquals(HttpSecurityPolicy.CheckResult.DENY, result);
        verify(identity, never()).checkPermission(any(Permission.class));
        verify(accessPolicy).check(identity);
    }

    @Test
    void checkPermission_checksPagePermissionWhenAccessPolicyAllows()
    {
        AuthProperties properties = mock(AuthProperties.class);
        AccessPolicy accessPolicy = mock(AccessPolicy.class);
        SecurityIdentity identity = mock(SecurityIdentity.class);
        RoutingContext request = mock(RoutingContext.class);
        HttpSecurityPolicy.AuthorizationRequestContext requestContext = mock(HttpSecurityPolicy.AuthorizationRequestContext.class);
        when(properties.enabled()).thenReturn(true);
        when(request.normalizedPath()).thenReturn("/impact/test");
        when(accessPolicy.check(identity)).thenReturn(HttpSecurityPolicy.CheckResult.PERMIT);
        when(identity.checkPermission(any(Permission.class))).thenReturn(Uni.createFrom().item(true));

        UiPagePolicy policy = new UiPagePolicy(accessPolicy, properties);
        HttpSecurityPolicy.CheckResult result = policy.checkPermission(
            request,
            Uni.createFrom().item(identity),
            requestContext
        ).await().indefinitely();

        assertEquals(HttpSecurityPolicy.CheckResult.PERMIT, result);
        verify(accessPolicy).check(identity);
        verify(identity).checkPermission(any(Permission.class));
    }
}
