package tech.illuin.wombat.ui;

import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.illuin.wombat.engine.context.model.UnrecognizedAsset;
import tech.illuin.wombat.engine.context.persistence.AssetRepository;
import tech.illuin.wombat.core.connector.boavizta.connector.BoaviztaClient;
import tech.illuin.wombat.engine.connector.boavizta.BoaviztaTestData;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.core.connector.ecologits.connector.EcologitsClient;
import tech.illuin.wombat.engine.connector.ecologits.EcologitsTestData;
import tech.illuin.wombat.engine.context.persistence.AssetEntity;
import tech.illuin.wombat.engine.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.persistence.model.KubernetesMetricEntity;

import java.time.LocalDate;
import java.time.ZoneOffset;


import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;

@QuarkusTest
class UIControllerTest
{
    @Inject BoaviztaClient boaviztaClient;
    @Inject EcologitsClient ecologitsClient;
    @Inject KubernetesMetricRepository datapointRepository;
    @Inject AssetRepository assetRepository;

    @BeforeEach
    @Transactional
    void seed()
    {
        datapointRepository.deleteAll();
        Mockito.reset(boaviztaClient, ecologitsClient);
        Mockito.when(boaviztaClient.getInstanceConfig(any(), any())).thenReturn(BoaviztaTestData.fakeInstanceConfig(8));
        Mockito.when(boaviztaClient.getInstanceImpact(anyBoolean(), anyInt(), any(), any())).thenReturn(BoaviztaTestData.fakeImpactResponse());
        Mockito.when(ecologitsClient.estimate(any())).thenReturn(EcologitsTestData.fakeEstimation());

        long now = System.currentTimeMillis();
        datapointRepository.save(metricRow(now, "podA", "api", 100.0));
        datapointRepository.save(metricRow(now, "podA", "worker", 300.0));
    }

    private static KubernetesMetricEntity metricRow(long instantMs, String pod, String container, double cpu)
    {
        KubernetesMetricEntity row = new KubernetesMetricEntity();
        row.instantMs = instantMs;
        row.windowMs = 3_600_000L;
        // Only folded rows are served, so the seeded ones stand for hours compaction already ran on.
        row.compacted = true;
        row.assign(new AssetIdentity("test-cluster", "test", "Test Cluster"));
        row.assetType = "tech.illuin.wombat-module.kubernetes-api";
        row.data = new KubernetesData(container, "test-cluster", "test-ns", pod, cpu, 0.0);
        row.cpuNanocores = cpu;
        return row;
    }

    @Test
    void get_withSeededMetrics_rendersImpactPage()
    {
        given()
            .when().get("/")
            .then()
            .statusCode(200)
            .body(containsString("Environmental Impact"))
            .body(containsString("api"))
            .body(containsString("worker"))
            .body(containsString("icons/docker.svg"))
            .body(not(containsString("unrecognizedAssetsTrigger")));
    }

    /** With security disabled there is nobody to name, so the header carries no signed-in indicator. */
    @Test
    void get_withSecurityDisabled_rendersNoUserIndicator()
    {
        given()
            .when().get("/")
            .then()
            .statusCode(200)
            .body(not(containsString("header-user")))
            .body(not(containsString("href=\"/logout\"")));
    }

    @Test
    void get_withUnrecognizedAssets_rendersWarningIconAndModal()
    {
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            String rawJson = "{\"id\":\"unrec-ui-1\",\"environment-id\":\"test\",\"name\":\"Dropped Extension Asset\",\"type\":\"tech.illuin.dropped.ModuleAsset\"}";
            UnrecognizedAsset unrecognized = new UnrecognizedAsset(
                AssetIdentity.of("unrec-ui-1", "test", "Dropped Extension Asset"), "tech.illuin.dropped.ModuleAsset", rawJson
            );
            AssetEntity entity = new AssetEntity();
            entity.id = "unrec-ui-1";
            entity.environmentId = "test";
            entity.name = "Dropped Extension Asset";
            entity.type = "tech.illuin.dropped.ModuleAsset";
            entity.properties = unrecognized;
            assetRepository.persist(entity);
        });

        try {
            given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body(containsString("unrecognizedAssetsTrigger"))
                .body(containsString("unrecognizedAssetsModal"))
                .body(containsString("Dropped Extension Asset"))
                .body(containsString("unrec-ui-1"))
                .body(containsString("tech.illuin.dropped.ModuleAsset"));
        }
        finally {
            io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
                assetRepository.delete("id", "unrec-ui-1");
            });
        }
    }

    @Test
    void get_withAssetParam_selectsThatAsset()
    {
        given()
            .queryParam("services", "test-cluster")
            .when().get("/")
            .then()
            .statusCode(200)
            .body(containsString("c5.large"))
            .body(containsString("Test Cluster"))
            .body(containsString("vCPU"))
            .body(containsString("Memory"))
            .body(containsString("CPU used"))
            .body(containsString("Load factor"));
    }

    @Test
    void get_withLLMAsset_rendersLLMBreakdown()
    {
        given()
            .when().get("/")
            .then()
            .statusCode(200)
            .body(containsString("Test LLM"))
            .body(containsString("mistral-large-latest"))
            .body(containsString("Output tokens total"))
            .body(containsString("Estimated requests / year"))
            .body(containsString("GWP / request"));
    }

    @Test
    void get_withLLMAsset_includesLLMServiceInBreakdownTableAndCounts()
    {
        given()
            .when().get("/")
            .then()
            .statusCode(200)
            .body(containsString("Test LLM / mistral-large-latest"))
            .body(containsString("Docker services"))
            .body(containsString("LLM services"))
            .body(containsString("icons/llm.svg"));
    }

    @Test
    void get_withLLMAsset_modelIsListedInServicePicker()
    {
        given()
            .when().get("/")
            .then()
            .statusCode(200)
            .body(containsString("value=\"mistral-large-latest\""));
    }

    @Test
    void get_withModelFilteredOut_excludesLLMFromAggregatesButKeepsAssetBlock()
    {
        given()
            .queryParam("services", "test-llm=api")
            .when().get("/")
            .then()
            .statusCode(200)
            .body(not(containsString("Test LLM / mistral-large-latest")))
            .body(containsString("Test LLM"));
    }

    @Test
    void get_withOnlyLLMAssetSelected_rendersWithoutKubernetesMetrics()
    {
        given()
            .queryParam("services", "test-llm")
            .when().get("/")
            .then()
            .statusCode(200)
            .body(containsString("Test LLM"))
            .body(containsString("mistral-large-latest"));
    }

    /** The range the page opens on is three months, which is charted a day at a time. */
    @Test
    void get_withSeededMetrics_rendersADailyHistogram()
    {
        given()
            .when().get("/")
            .then()
            .statusCode(200)
            .body(containsString("Impact over time"))
            .body(containsString("daily</div>"))
            .body(containsString("chartImpactTimeline"))
            .body(containsString("var timelineStarts"))
            .body(containsString("var timelineStepMs = 86400000"));
    }

    /** A week or less is the one case the columns go hourly, so intraday swings stay visible. */
    @Test
    void get_withAShortRange_rendersAnHourlyHistogram()
    {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        given()
            .queryParam("from", today.minusDays(1) + "T00:00")
            .queryParam("to", today.plusDays(1) + "T00:00")
            .when().get("/")
            .then()
            .statusCode(200)
            .body(containsString("hourly</div>"))
            .body(containsString("var timelineStepMs = 3600000"));
    }

    @Test
    void get_withSeededMetrics_offersTheThreeMetricsOfTheHistogram()
    {
        given()
            .when().get("/")
            .then()
            .statusCode(200)
            .body(containsString("data-series=\"gwp\""))
            .body(containsString("data-series=\"pe\""))
            .body(containsString("data-series=\"adp\""))
            .body(containsString("key: \"adp\", label: \"Abiotic resources\", unit: \"kgSbeq\""));
    }

    @Test
    void get_withCustomTimeRange_acceptsAndRenders()
    {
        given()
            .queryParam("from", "1970-01-01T00:00")
            .queryParam("to", "2099-01-01T00:00")
            .when().get("/")
            .then()
            .statusCode(200);
    }

    @Test
    void get_withIntradayRange_snapsBoundsToMidnightUtc()
    {
        given()
            .queryParam("from", "2026-06-04T13:30")
            .queryParam("to", "2026-06-10T08:15")
            .when().get("/")
            .then()
            .statusCode(200)
            .body(containsString("data-from-utc=\"2026-06-04T00:00:00Z\""))
            .body(containsString("data-to-utc=\"2026-06-11T00:00:00Z\""));
    }

    @Test
    void get_withMidnightRange_leavesBoundsUntouched()
    {
        given()
            .queryParam("from", "2026-06-04T00:00")
            .queryParam("to", "2026-06-11T00:00")
            .when().get("/")
            .then()
            .statusCode(200)
            .body(containsString("data-from-utc=\"2026-06-04T00:00:00Z\""))
            .body(containsString("data-to-utc=\"2026-06-11T00:00:00Z\""));
    }

    @Test
    void get_legacyUiPath_redirectsToDashboard()
    {
        given()
            .redirects().follow(false)
            .when().get("/ui")
            .then()
            .statusCode(302)
            .header("Location", "/");
    }

    @Test
    void get_legacyUiPathWithQuery_keepsTheQueryString()
    {
        given()
            .redirects().follow(false)
            .when().get("/ui?services=test-cluster&environment=test")
            .then()
            .statusCode(302)
            .header("Location", "/?services=test-cluster&environment=test");
    }

    @Test
    void get_unknownPage_redirectsToDashboard()
    {
        given()
            .redirects().follow(false)
            .when().get("/does-not-exist")
            .then()
            .statusCode(302)
            .header("Location", "/");
    }

    @Test
    void get_staticAssetsAndApi_areNotRedirected()
    {
        given()
            .redirects().follow(false)
            .when().get("/public/impact.css")
            .then()
            .statusCode(200);

        given()
            .redirects().follow(false)
            .when().get("/api/environments")
            .then()
            .statusCode(200);

        given()
            .redirects().follow(false)
            .when().get("/q/metrics")
            .then()
            .statusCode(200);
    }

    @Test
    void post_unknownPath_isNotRedirected()
    {
        given()
            .redirects().follow(false)
            .when().post("/does-not-exist")
            .then()
            .statusCode(404);
    }
}
