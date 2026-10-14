package tech.illuin.wombat.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.activity.commons.TimeRange;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

import io.pebbletemplates.pebble.PebbleEngine;

import static org.junit.jupiter.api.Assertions.*;

class TemplateRendererTest
{
    private final TemplateRenderer renderer = new TemplateRenderer(new UIConfig().templateEngine(), new ObjectMapper(), null, null);

    @Test
    void templatesRemainVisibleWhenDependenciesCannotSeeApplicationResources() throws Exception
    {
        URL library = PebbleEngine.class.getProtectionDomain().getCodeSource().getLocation();
        URL application = UIConfig.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader dependencyLoader = new URLClassLoader(new URL[] {library}, UIConfig.class.getClassLoader())
        {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
            {
                if (!name.startsWith("io.pebbletemplates.")) return super.loadClass(name, resolve);
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) loaded = findClass(name);
                if (resolve) resolveClass(loaded);
                return loaded;
            }

            @Override
            public InputStream getResourceAsStream(String name)
            {
                return name.startsWith("templates/") ? null : super.getResourceAsStream(name);
            }
        };
             URLClassLoader applicationLoader = new URLClassLoader(new URL[] {application}, dependencyLoader)
             {
                 @Override
                 protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException
                 {
                     if (!name.equals(UIConfig.class.getName()) && !name.startsWith(WombatPebbleExtension.class.getName()))
                         return super.loadClass(name, resolve);
                     Class<?> loaded = findLoadedClass(name);
                     if (loaded == null) loaded = findClass(name);
                     if (resolve) resolveClass(loaded);
                     return loaded;
                 }
             })
        {
            assertNull(dependencyLoader.getResourceAsStream("templates/pages/impact/index.peb"));
            Class<?> configType = applicationLoader.loadClass(UIConfig.class.getName());
            Object engine = configType.getMethod("templateEngine").invoke(configType.getConstructor().newInstance());
            assertNotNull(engine.getClass().getMethod("getTemplate", String.class).invoke(engine, "pages/impact/index"));
        }
    }

    @Test
    void jsonCannotTerminateItsScriptElement() throws Exception
    {
        String label = "</script><img src=x onerror=alert(1)>\"&";
        String json = this.renderer.scriptSafeJson(Map.of("label", label));
        assertFalse(json.contains("<"));
        assertEquals(label, new ObjectMapper().readTree(json).get("label").asText());
    }

    @Test
    void signedOutPageDoesNotShowAUser() throws IOException
    {
        String html = this.renderer.loggedOut(false);
        assertTrue(html.contains("Signed out"));
        assertTrue(html.contains("identity provider session may still be active"));
        assertFalse(html.contains("header-user"));
        writePreview("logged-out.html", html);
    }

    @Test
    void errorPageUsesSharedDateControlAndLocalAssets() throws IOException
    {
        String html = this.renderer.impactError(
            new TimeRange(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-02T00:00:00Z")),
            new Templates.MaxSpan(86_400_000, "1 day")
        );
        assertTrue(html.contains("2026-10-01T00:00:00Z"));
        assertTrue(html.contains("/public/vendor/easepick.umd.min.js"));
        assertTrue(html.contains("src=\"/public/ui/app.js\""));
        assertFalse(html.contains("v-scope"));
        assertFalse(html.contains("petite-vue"));
        assertTrue(html.contains("data-vue-filters=\"dates\""));
        assertFalse(html.contains("id=\"filterForm\""));
        assertTrue(html.contains("href=\"/public/dmsans.css\""));
        assertTrue(html.contains("href=\"/public/impact.css\""));
        assertTrue(html.contains("href=\"/public/icons/wombat-medium-mask.png\""));
        assertTrue(html.contains("src=\"/public/icons/wombat-large-mask.png\""));
        assertTrue(html.contains("No CPU usage found"));
        writePreview("error.html", html);
    }

    @Test
    void userNamesAreEscapedAndNotRetainedAcrossRequests()
    {
        Map<String, Object> data = Map.of(
            "timeRange", new TimeRange(Instant.EPOCH, Instant.EPOCH.plusSeconds(86_400)),
            "maxSpan", new Templates.MaxSpan(86_400_000, "1 day")
        );
        String authenticated = this.renderer.render("pages/impact/error", data, new CurrentUser(true, "<script>bad</script>"));
        assertTrue(authenticated.contains("&lt;script&gt;bad&lt;/script&gt;"));
        assertFalse(authenticated.contains("<script>bad</script>"));
        assertFalse(this.renderer.render("pages/impact/error", data, CurrentUser.ANONYMOUS).contains("header-user"));
    }

    @Test
    void reportProvidesPrecompiledVueIslandsAndInertServerContent() throws IOException
    {
        TimeRange range = new TimeRange(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-03T00:00:00Z"));
        Map<String, Object> metric = Map.of("embedded", Map.of("value", 1), "use", Map.of("value", 2), "totalValue", 3);
        Map<String, Object> footprint = Map.of("gwp", metric, "pe", metric, "adp", metric);
        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("name", "Cluster {{ globalThis.wombatUnsafe = true }}");
        panel.put("regime", Map.of("label", "Measured"));
        panel.put("serviceIds", List.of("api", "worker"));
        panel.put("clusters", List.of(Map.of("id", "cluster", "namespace", "default")));
        panel.put("provider", "aws");
        panel.put("instanceType", "c5.large");
        panel.put("location", "FRA");
        panel.put("lifespan", 43800);
        panel.put("specification", Map.of("vcpu", 2, "memory", 4));
        panel.put("load", Map.of("cpuUsageCores", 1, "loadPercent", 50, "nodeCount", 1));
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("timeRange", range);
        env.put("globalImpact", footprint);
        env.put("dockerImpact", footprint);
        env.put("llmImpact", footprint);
        env.put("assetCount", 1);
        env.put("serviceCount", 2);
        env.put("dockerServiceCount", 2);
        env.put("llmServiceCount", 0);
        env.put("allServices", List.of("api", "worker"));
        env.put("includedServices", List.of("api", "worker"));
        env.put("kubernetesPanels", List.of(panel));
        env.put("llmPanels", List.of());
        env.put("impactShares", Map.of("Cluster / api", 1.0));
        env.put("serviceImpacts", List.of(Map.of(
            "assetName", "Cluster", "service", "api", "label", "Cluster / api", "footprint", footprint,
            "assetType", Map.of("family", Map.of("name", "KUBERNETES_CONTAINER"))
        )));
        env.put("unrecognizedAssets", List.of(Map.of(
            "identity", Map.of("name", "<unknown> {{ globalThis.wombatUnsafe = true }}", "id", "unknown"),
            "rawType", "unknown.Type"
        )));
        Map<String, Object> series = Map.of("key", "gwp", "label", "GWP", "unit", "kgCO2eq",
            "buckets", List.of(Map.of("embedded", 1, "use", 2)));
        Map<String, Object> browserMetric = Map.of("embedded", 1, "use", 2);
        String pageData = this.renderer.scriptSafeJson(Map.of(
            "services", List.of(Map.of("name", "api", "asset", "Cluster", "share", 1.0,
                "gwp", browserMetric, "pe", browserMetric, "adp", browserMetric)),
            "timeline", Map.of("starts", List.of(range.start().toEpochMilli()), "stepMs", 86400000, "series", List.of(series))
        ));
        String html = this.renderer.render("pages/impact/index", Map.of(
            "env", env, "assets", Map.of("all", List.of(Map.of("identity",
                Map.of("name", "Cluster", "id", "cluster"), "namespace", "default")), "selectedIds", List.of("cluster")),
            "environments", new Templates.EnvironmentSelection(List.of(new Templates.EnvironmentView("test", "<Test>")), "test"),
            "maxSpan", new Templates.MaxSpan(0, ""), "timeline", Map.of("empty", false, "stepLabel", "day",
                "bucketCount", 2, "stepPlural", "days", "series", List.of(series)), "pageData", pageData
        ), CurrentUser.ANONYMOUS);
        writePreview("vue.html", html);
        assertTrue(html.contains("data-vue-filters=\"report\""));
        assertTrue(html.contains("data-vue-modal"));
        assertTrue(html.contains("data-vue-service=\"0\""));
        assertTrue(html.contains("src=\"/public/ui/app.js\""));
        assertTrue(html.contains("data-regime=\"Measured\""));
        assertTrue(html.contains("&lt;Test&gt;"));
        assertTrue(html.contains("&lt;unknown&gt;"));
        assertTrue(html.contains("globalThis.wombatUnsafe"));
        assertFalse(html.contains("v-scope"));
        assertFalse(html.contains("@vue:"));
        assertTrue(html.contains("<template id=\"kubernetes-0\""));
        assertTrue(html.contains("data-title=\"Cluster {{ globalThis.wombatUnsafe = true }}\""));
        assertTrue(html.contains("<option value=\"test\""));
        assertTrue(html.contains("data-vue-timeline"));
        assertFalse(html.contains("<canvas"));
        assertFalse(html.contains("class=\"asset-modal-overlay\""));
        assertFalse(html.contains("class=\"container-picker\""));
        assertFalse(html.contains("class=\"service-detail\""));
        assertFalse(html.contains("id=\"themeToggle\""));
    }

    private static void writePreview(String name, String html) throws IOException
    {
        String previewDirectory = System.getProperty("test.preview.directory");
        if (previewDirectory != null)
        {
            Path directory = Path.of(previewDirectory);
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(name), html);
        }
    }
}
