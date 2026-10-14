package tech.illuin.wombat.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pebbletemplates.pebble.PebbleEngine;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.security.AuthProperties;
import tech.illuin.wombat.engine.impact.timeline.ImpactTimeline;

import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class TemplateRenderer
{
    private final PebbleEngine engine;
    private final ObjectMapper mapper;
    private final SecurityIdentity identity;
    private final AuthProperties authProperties;

    public TemplateRenderer(PebbleEngine engine, ObjectMapper mapper, SecurityIdentity identity, AuthProperties authProperties)
    {
        this.engine = engine;
        this.mapper = mapper;
        this.identity = identity;
        this.authProperties = authProperties;
    }

    public String impact(EnvironmentImpact env, Templates.AssetSelection assets, Templates.EnvironmentSelection environments,
                         Templates.MaxSpan maxSpan, ImpactTimeline timeline)
    {
        return render("pages/impact/index", Map.of(
            "env", env,
            "assets", Map.of(
                "all", assets.all().stream().map(asset -> new Templates.AssetView(asset.identity(), TemplateFormatters.namespace(asset))).toList(),
                "selectedIds", assets.selectedIds()
            ),
            "environments", environments,
            "maxSpan", maxSpan,
            "timeline", timeline,
            "pageData", scriptSafeJson(ImpactPageData.of(env, timeline))
        ), this.currentUser());
    }

    public String impactError(TimeRange timeRange, Templates.MaxSpan maxSpan)
    {
        return render("pages/impact/error", Map.of("timeRange", timeRange, "maxSpan", maxSpan), this.currentUser());
    }

    public String loggedOut(boolean idpSessionEnded)
    {
        return render("pages/session/logged-out", Map.of("idpSessionEnded", idpSessionEnded), CurrentUser.ANONYMOUS);
    }

    private CurrentUser currentUser()
    {
        if (this.identity == null || this.identity.isAnonymous())
            return CurrentUser.ANONYMOUS;
        return CurrentUser.from(this.identity, this.authProperties.displayNameClaims());
    }

    String render(String name, Map<String, Object> values, CurrentUser currentUser)
    {
        Map<String, Object> context = new LinkedHashMap<>(values);
        context.put("currentUser", currentUser);
        try {
            StringWriter writer = new StringWriter();
            this.engine.getTemplate(name).evaluate(writer, context, Locale.US);
            return writer.toString();
        }
        catch (IOException e) {
            throw new IllegalStateException("Failed to render template " + name, e);
        }
    }

    String scriptSafeJson(Object value)
    {
        try {
            return this.mapper.writeValueAsString(value).replace("<", "\\u003c");
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize impact page data", e);
        }
    }
}
