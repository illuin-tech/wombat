package tech.illuin.wombat.endpoint;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.connector.boavizta.connector.BoaviztaClient;
import tech.illuin.wombat.core.connector.ecologits.connector.EcologitsClient;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.engine.connector.boavizta.BoaviztaTestData;
import tech.illuin.wombat.engine.connector.ecologits.EcologitsTestData;
import tech.illuin.wombat.engine.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.engine.simulation.persistence.SimulationAssetRepository;
import tech.illuin.wombat.engine.simulation.persistence.SimulationRepository;
import tech.illuin.wombat.persistence.model.KubernetesMetricEntity;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Evaluates copies of the provisioned {@code test-cluster} (measured, its metrics seeded two days ago) and
 * {@code test-llm} (modeled), against the mocked Boavizta and Ecologits clients.
 */
@QuarkusTest
class SimulationSandboxEvaluationControllerTest
{
    private static final String SIMULATION = "sim-evaluation";

    @Inject SimulationRepository simulations;
    @Inject SimulationAssetRepository simulationAssets;
    @Inject KubernetesMetricRepository metrics;
    @Inject BoaviztaClient boaviztaClient;
    @Inject EcologitsClient ecologitsClient;

    @BeforeEach
    void setUp()
    {
        this.clean();
        Mockito.reset(this.boaviztaClient, this.ecologitsClient);
        Mockito.when(this.boaviztaClient.getInstanceConfig(any(), any())).thenReturn(BoaviztaTestData.fakeInstanceConfig(8));
        Mockito.when(this.boaviztaClient.getInstanceImpact(anyBoolean(), anyInt(), any(), any())).thenReturn(BoaviztaTestData.fakeImpactResponse());
        Mockito.when(this.ecologitsClient.estimate(any())).thenReturn(EcologitsTestData.fakeEstimation());

        given().contentType(ContentType.JSON).body(Map.of("id", SIMULATION, "name", "Evaluated")).when().post("/api/simulation/sandbox").then().statusCode(201);
    }

    @AfterEach
    void clean()
    {
        SimulationSandboxControllerTest.clean(this.simulations, this.simulationAssets, List.of(SIMULATION));
        QuarkusTransaction.requiringNew().run(() -> this.metrics.deleteAll());
    }

    @Test
    void aCopy_readsItsSourcesMetrics()
    {
        this.seedClusterMetrics();
        this.copy("test-cluster");

        this.evaluate(Map.of())
            .then()
            .statusCode(200)
            .body("payload.window.period", is("MONTH"))
            .body("payload.factors.MONTH", is(1.0f))
            .body("payload.assets", hasSize(1))
            .body("payload.assets[0].id", is("test-cluster"))
            .body("payload.assets[0].status", is("ok"))
            /* One day of thirty; JSON numbers are read as floats */
            .body("payload.assets[0].coverage", is((float) (1.0 / 30)))
            .body("payload.assets[0].result[0].provider", is("BOAVIZTA"))
            .body("payload.assets[0].result.type", is(List.of("IMPACT", "COST")))
            .body("payload.assets[0].result[0]", not(hasKey("assetIdentity")))
            .body("payload.assets[0].scaled.YEAR.impact.gwp", notNullValue())
            /* Evaluated, but priced by no module yet */
            .body("payload.assets[0].scaled.YEAR.cost", empty())
            .body("payload.assets[0].scaled.YEAR.services", not(empty()))
            .body("payload.assets[0].scaled.YEAR.services[0].impact.gwp", notNullValue())
            .body("payload.totals.DAY.impact.gwp", notNullValue());
    }

    @Test
    void aCopyWhoseSourceHasNoMetrics_hasNoData()
    {
        this.copy("test-cluster");

        this.evaluate(Map.of())
            .then()
            .statusCode(200)
            .body("payload.assets[0].status", is("no-data"))
            .body("payload.assets[0].message", notNullValue())
            .body("payload.assets[0].result", empty());
    }

    @Test
    void aModeledAsset_isEvaluatedFromItsDefinition()
    {
        this.copy("test-llm");

        this.evaluate(Map.of("referenceWindow", "WEEK", "scopes", List.of("IMPACT")))
            .then()
            .statusCode(200)
            .body("payload.window.period", is("WEEK"))
            .body("payload.assets[0].status", is("ok"))
            .body("payload.assets[0].result[0].provider", is("ECOLOGITS"))
            .body("payload.assets[0].result.type", is(List.of("IMPACT")));
        /* The window was overridden for this evaluation only */
        given().when().get("/api/simulation/sandbox/" + SIMULATION).then().body("payload.referenceWindow", is("MONTH"));
    }

    @Test
    void anEdit_reachesTheNextEvaluation_butNeverTheDashboard()
    {
        this.seedClusterMetrics();
        this.copy("test-cluster");
        Map<String, Object> properties = this.properties("/api/simulation/sandbox/" + SIMULATION + "/assets/test-cluster");
        this.withProfile(properties, "instanceType", "m5.xlarge");
        given().contentType(ContentType.JSON)
            .body(Map.of("name", "Bigger", "properties", properties))
            .when().put("/api/simulation/sandbox/" + SIMULATION + "/assets/test-cluster")
            .then().statusCode(200);

        this.evaluate(Map.of()).then().statusCode(200).body("payload.assets[0].status", is("ok"));
        verify(this.boaviztaClient, atLeastOnce()).getInstanceConfig(any(), eq("m5.xlarge"));

        Mockito.clearInvocations(this.boaviztaClient);
        Instant end = Instant.now().truncatedTo(ChronoUnit.DAYS);
        String body = """
            {
              "sourceTimeRange": { "start": "%s", "end": "%s" },
              "environments": [ { "id": "test", "assets": [ { "id": "test-cluster" } ] } ]
            }
            """.formatted(end.minus(Duration.ofDays(30)), end);
        given().contentType(ContentType.JSON).body(body).when().post("/api/impact").then().statusCode(200);
        verify(this.boaviztaClient, atLeastOnce()).getInstanceConfig(any(), eq("c5.large"));
        verify(this.boaviztaClient, never()).getInstanceConfig(any(), eq("m5.xlarge"));
    }

    @Test
    void twoCopiesOfOneSource_areEvaluatedApart()
    {
        this.copy("test-llm");
        this.copy("test-llm");
        Map<String, Object> properties = this.properties("/api/simulation/sandbox/" + SIMULATION + "/assets/test-llm-2");
        this.withModel(properties, "mistral-small-latest");
        given().contentType(ContentType.JSON)
            .body(Map.of("name", "Smaller", "properties", properties))
            .when().put("/api/simulation/sandbox/" + SIMULATION + "/assets/test-llm-2")
            .then().statusCode(200);

        this.evaluate(Map.of()).then().statusCode(200).body("payload.assets.status", is(List.of("ok", "ok")));
        verify(this.ecologitsClient, atLeastOnce()).estimate(argThat(request -> request.modelName().equals("mistral-large-latest")));
        verify(this.ecologitsClient, atLeastOnce()).estimate(argThat(request -> request.modelName().equals("mistral-small-latest")));
    }

    @Test
    void aSimulationWithoutAssets_evaluatesToNothing()
    {
        this.evaluate(Map.of()).then().statusCode(200).body("payload.assets", empty()).body("payload.totals.YEAR.impact.gwp", notNullValue());
    }

    @Test
    void anUnknownSimulation_returns404()
    {
        given().contentType(ContentType.JSON).body(Map.of()).when().post("/api/simulation/sandbox/does-not-exist/evaluation").then().statusCode(404);
    }

    private io.restassured.response.Response evaluate(Map<String, Object> body)
    {
        return given().contentType(ContentType.JSON).body(body).when().post("/api/simulation/sandbox/" + SIMULATION + "/evaluation");
    }

    private void copy(String sourceAssetId)
    {
        given().contentType(ContentType.JSON)
            .body(Map.of("name", "Copy of " + sourceAssetId, "sourceAssetId", sourceAssetId))
            .when().post("/api/simulation/sandbox/" + SIMULATION + "/assets")
            .then().statusCode(201);
    }

    /* Two days ago, an hour of CPU usage */
    private void seedClusterMetrics()
    {
        long instantMs = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(Duration.ofDays(2)).toEpochMilli();
        QuarkusTransaction.requiringNew().run(() -> {
            this.metrics.save(metricRow(instantMs, "api", 100.0));
            this.metrics.save(metricRow(instantMs, "worker", 300.0));
        });
    }

    private static KubernetesMetricEntity metricRow(long instantMs, String container, double cpu)
    {
        KubernetesMetricEntity row = new KubernetesMetricEntity();
        row.instantMs = instantMs;
        row.windowMs = 3_600_000L;
        row.compacted = true;
        row.assign(new AssetIdentity("test-cluster", "test", "tech.illuin.wombat-module.kubernetes-api"));
        row.data = new KubernetesData(container, "test-cluster", "test-ns", "podA", cpu, 0.0);
        row.cpuNanocores = cpu;
        return row;
    }

    /** The type-specific properties of a simulation asset, as the API serves them. */
    private Map<String, Object> properties(String path)
    {
        Map<String, Object> asset = new HashMap<>(given().when().get(path).then().statusCode(200).extract().path("payload"));
        for (String key : List.of("id", "environmentId", "type", "simulationId", "name", "description", "notes", "source",
            "createdAt", "updatedAt"))
            asset.remove(key);
        return asset;
    }

    @SuppressWarnings("unchecked")
    private void withProfile(Map<String, Object> properties, String key, Object value)
    {
        Map<String, Object> profile = new HashMap<>((Map<String, Object>) properties.get("profile"));
        profile.put(key, value);
        properties.put("profile", profile);
    }

    @SuppressWarnings("unchecked")
    private void withModel(Map<String, Object> properties, String model)
    {
        Map<String, Object> profile = (Map<String, Object>) properties.get("profile");
        Map<String, Object> first = new HashMap<>(((List<Map<String, Object>>) profile.get("models")).getFirst());
        first.put("model", model);
        this.withProfile(properties, "models", List.of(first));
    }
}
