package tech.illuin.wombat.security;

import io.quarkus.security.StringPermission;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UiPagePolicy implements HttpSecurityPolicy
{
    static final String NAME = "wombat-ui-page";

    private final AccessPolicy accessPolicy;
    private final AuthProperties properties;

    public UiPagePolicy(AccessPolicy accessPolicy, AuthProperties properties)
    {
        this.accessPolicy = accessPolicy;
        this.properties = properties;
    }

    @Override
    public Uni<CheckResult> checkPermission(RoutingContext request, Uni<SecurityIdentity> identity, AuthorizationRequestContext requestContext)
    {
        return identity.flatMap(current -> {
            if (current == null || (this.properties.enabled() && this.accessPolicy.check(current) != CheckResult.PERMIT))
                return Uni.createFrom().item(CheckResult.DENY);

            return current.checkPermission(new StringPermission(requiredFor(request.normalizedPath()).value()))
                .map(allowed -> allowed ? CheckResult.PERMIT : CheckResult.DENY);
        });
    }

    @Override
    public String name()
    {
        return NAME;
    }

    static Permission requiredFor(String path)
    {
        if (path.equals("/users") || path.startsWith("/users/"))
            return Permission.UI_USERS_READ;
        if (path.equals("/api-keys") || path.startsWith("/api-keys/"))
            return Permission.UI_APIKEYS_MANAGE;
        return Permission.UI_DASHBOARD_READ;
    }
}
