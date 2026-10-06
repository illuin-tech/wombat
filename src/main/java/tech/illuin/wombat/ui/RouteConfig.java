package tech.illuin.wombat.ui;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class RouteConfig
{
    private static final String DASHBOARD_PATH = "/";

    private static final Logger logger = LoggerFactory.getLogger(RouteConfig.class);

    public void registerDashboardFallback(@Observes Router router)
    {
        router.route().order(Integer.MAX_VALUE).handler(context -> {
            HttpMethod method = context.request().method();
            boolean readOnly = HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method);
            boolean oidcCallback = context.request().getParam("code") != null && context.request().getParam("state") != null;

            if (!readOnly || oidcCallback)
            {
                context.next();
                return;
            }

            String query = context.request().query();
            String target = query == null || query.isBlank() ? DASHBOARD_PATH : DASHBOARD_PATH + "?" + query;

            logger.debug("No route for {}, redirecting to {}", context.request().uri(), target);
            context.response()
                .setStatusCode(302)
                .putHeader(HttpHeaders.LOCATION, target)
                .end();
        });
    }
}
