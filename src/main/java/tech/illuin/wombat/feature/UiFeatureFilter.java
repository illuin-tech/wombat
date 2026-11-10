package tech.illuin.wombat.feature;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.quarkus.vertx.http.runtime.security.SecurityHandlerPriorities;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class UiFeatureFilter
{
    static final int PRIORITY = (SecurityHandlerPriorities.CORS + SecurityHandlerPriorities.AUTHENTICATION) / 2;

    private final FeatureFlags features;

    public UiFeatureFilter(FeatureFlags features)
    {
        this.features = features;
    }

    void register(@Observes Filters filters)
    {
        filters.register(this::filter, PRIORITY);
    }

    private void filter(RoutingContext context)
    {
        if (!isUiPath(context.normalizedPath()))
        {
            context.next();
            return;
        }

        this.features.isEnabled(Feature.UI).subscribe().with(
            enabled -> {
                if (enabled)
                    context.next();
                else
                    context.response().setStatusCode(404).end();
            },
            context::fail
        );
    }

    static boolean isUiPath(String path)
    {
        return !path.startsWith("/api/") && !path.startsWith("/q/");
    }
}
