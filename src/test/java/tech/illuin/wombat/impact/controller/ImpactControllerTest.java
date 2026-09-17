package tech.illuin.wombat.impact.controller;

import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.illuin.wombat.core.connector.boavizta.connector.BoaviztaClient;
import tech.illuin.wombat.connector.boavizta.BoaviztaTestData;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.core.connector.ecologits.connector.EcologitsClient;
import tech.illuin.wombat.connector.ecologits.EcologitsTestData;
import tech.illuin.wombat.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.persistence.model.KubernetesMetricEntity;


import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;

@QuarkusTest
class ImpactControllerTest
{

    @Inject
    BoaviztaClient boaviztaClient;

    @Inject
    EcologitsClient ecologitsClient;

    @Inject
    KubernetesMetricRepository datapointRepository;

    @BeforeEach
    @Transactional
    void seedMetrics()
    {
        datapointRepository.deleteAll();
        Mockito.reset(boaviztaClient, ecologitsClient);
        Mockito.when(boaviztaClient.getInstanceConfig(any(), any())).thenReturn(BoaviztaTestData.fakeInstanceConfig(8));
        Mockito.when(boaviztaClient.getInstanceImpact(anyBoolean(), anyInt(), any(), any())).thenReturn(BoaviztaTestData.fakeImpactResponse());
        Mockito.when(ecologitsClient.estimate(any())).thenReturn(EcologitsTestData.fakeEstimation());

        datapointRepository.save(metricRow(1_000L, "podA", "api", 100.0));
        datapointRepository.save(metricRow(1_000L, "podA", "worker", 300.0));
    }

    private static KubernetesMetricEntity metricRow(long instantMs, String pod, String container, double cpu)
    {
        KubernetesMetricEntity row = new KubernetesMetricEntity();
        row.instantMs = instantMs;
        row.data = new KubernetesData(container, "test-asset", "test-env", "test-cluster", "test-ns", pod, cpu, 0.0);
        row.cpuNanocores = cpu;
        return row;
    }

    @Test
    void getImpact_withExplicitAssetId_returnsFootprintForThatAsset()
    {
        String body = """
            {
              "source_time_range": { "start": "1970-01-01T00:00:00Z", "end": "1970-01-01T00:00:10Z" },
              "environments": [ { "id": "test", "assets": [ { "id": "test-cluster" } ] } ]
            }
            """;

        given()
            .contentType("application/json").body(body)
            .when().post("/impact")
            .then()
            .statusCode(200)
            .body("payload.size()", is(1))
            .body("payload[0].assetId", is("test-cluster"))
            .body("payload[0].environmentId", is("test"))
            .body("payload[0].provider", is("BOAVIZTA"))
            .body("payload[0].footprint.gwp", notNullValue())
            .body("payload[0].serviceImpacts.size()", greaterThan(0))
            .body("payload[0].serviceImpacts[0].assetType", is("KUBERNETES_API"));
    }

    @Test
    void getImpact_withoutAssetIds_defaultsToAllConfiguredAssets()
    {
        String body = """
            {
              "source_time_range": { "start": "1970-01-01T00:00:00Z", "end": "1970-01-01T00:00:10Z" }
            }
            """;

        given()
            .contentType("application/json").body(body)
            .when().post("/impact")
            .then()
            .statusCode(200)
            .body("payload.size()", is(2))
            .body("payload.assetId", containsInAnyOrder("test-cluster", "test-llm"));
    }

    @Test
    void getImpact_withEnvironmentId_returnsAllAssetsOfThatEnvironment()
    {
        String body = """
            {
              "source_time_range": { "start": "1970-01-01T00:00:00Z", "end": "1970-01-01T00:00:10Z" },
              "environments": [ { "id": "test" } ]
            }
            """;

        given()
            .contentType("application/json").body(body)
            .when().post("/impact")
            .then()
            .statusCode(200)
            .body("payload.size()", is(2))
            .body("payload.environmentId", containsInAnyOrder("test", "test"))
            .body("payload.find { it.assetId == 'test-llm' }.provider", is("ECOLOGITS"))
            .body("payload.find { it.assetId == 'test-llm' }.footprint.gwp", notNullValue())
            .body("payload.find { it.assetId == 'test-llm' }.serviceImpacts[0].assetType", is("LLM_STATIC"))
            .body("payload.find { it.assetId == 'test-llm' }.serviceImpacts[0].profile.model", is("mistral-large-latest"))
            .body("payload.find { it.assetId == 'test-cluster' }.provider", is("BOAVIZTA"));
    }

    @Test
    void getImpact_withUnknownEnvironmentId_returns400()
    {
        String body = """
            {
              "source_time_range": { "start": "1970-01-01T00:00:00Z", "end": "1970-01-01T00:00:10Z" },
              "environments": [ { "id": "unknown-env" } ]
            }
            """;

        given()
            .contentType("application/json").body(body)
            .when().post("/impact")
            .then()
            .statusCode(400);
    }

    @Test
    void getImpact_environmentWithoutId_returns400()
    {
        String body = """
            {
              "source_time_range": { "start": "1970-01-01T00:00:00Z", "end": "1970-01-01T00:00:10Z" },
              "environments": [ { "assets": [ { "id": "test-cluster" } ] } ]
            }
            """;

        given()
            .contentType("application/json").body(body)
            .when().post("/impact")
            .then()
            .statusCode(400);
    }

    @Test
    void getEnvironments_listsConfiguredEnvironments()
    {
        given()
            .when().get("/impact/environments")
            .then()
            .statusCode(200)
            .body("payload.size()", is(1))
            .body("payload[0].id", is("test"))
            .body("payload[0].assets.size()", is(2))
            .body("payload[0].assets.id", containsInAnyOrder("test-cluster", "test-llm"))
            .body("payload[0].time_range", nullValue());
    }

    @Test
    void getAssets_returnsAssetSummariesOfEnvironment()
    {
        given()
            .when().get("/impact/environments/test/assets")
            .then()
            .statusCode(200)
            .body("payload.size()", is(2))
            .body("payload.type", containsInAnyOrder("KUBERNETES_API", "LLM_STATIC"))
            .body("payload.find { it.id == 'test-cluster' }.name", is("Test Cluster"));
    }

    @Test
    void getAssets_unknownEnvironment_returns404()
    {
        given()
            .when().get("/impact/environments/unknown-env/assets")
            .then()
            .statusCode(404);
    }

    @Transactional
    void emptyDatapoints()
    {
        datapointRepository.deleteAll();
    }
}
