package tech.illuin.wombat.endpoint;

import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.illuin.wombat.core.connector.boavizta.connector.BoaviztaClient;
import tech.illuin.wombat.engine.connector.boavizta.BoaviztaTestData;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.core.connector.ecologits.connector.EcologitsClient;
import tech.illuin.wombat.engine.connector.ecologits.EcologitsTestData;
import tech.illuin.wombat.engine.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.persistence.model.KubernetesMetricEntity;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    @Inject BoaviztaClient boaviztaClient;
    @Inject EcologitsClient ecologitsClient;
    @Inject KubernetesMetricRepository datapointRepository;

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
        row.windowMs = 3_600_000L;
        row.compacted = true;
        row.assign(new AssetIdentity("test-cluster", "test", "Test Cluster"));
        row.assetType = "tech.illuin.wombat-module.kubernetes-api";
        row.data = new KubernetesData(container, "test-cluster", "test-ns", pod, cpu, 0.0);
        row.cpuNanocores = cpu;
        return row;
    }

    @Test
    void getImpact_withExplicitAssetId_returnsFootprintForThatAsset()
    {
        String body = """
            {
              "sourceTimeRange": { "start": "1970-01-01T00:00:00Z", "end": "1970-01-01T00:00:10Z" },
              "environments": [ { "id": "test", "assets": [ { "id": "test-cluster" } ] } ]
            }
            """;

        given()
            .contentType("application/json").body(body)
            .when().post("/api/impact")
            .then()
            .statusCode(200)
            .body("payload.size()", is(1))
            .body("payload[0].assetIdentity.id", is("test-cluster"))
            .body("payload[0].assetIdentity.environmentId", is("test"))
            .body("payload[0].provider", is("BOAVIZTA"))
            .body("payload[0].footprint.gwp", notNullValue())
            .body("payload[0].serviceImpacts.size()", greaterThan(0))
            .body("payload[0].serviceImpacts[0].assetType.name", is("tech.illuin.wombat-module.kubernetes-api"));
    }

    @Test
    void getImpact_withoutAssetIds_defaultsToAllConfiguredAssets()
    {
        String body = """
            {
              "sourceTimeRange": { "start": "1970-01-01T00:00:00Z", "end": "1970-01-01T00:00:10Z" }
            }
            """;

        given()
            .contentType("application/json").body(body)
            .when().post("/api/impact")
            .then()
            .statusCode(200)
            .body("payload.size()", is(2))
            .body("payload.assetIdentity.id", containsInAnyOrder("test-cluster", "test-llm"));
    }

    @Test
    void getImpact_withEnvironmentId_returnsAllAssetsOfThatEnvironment()
    {
        String body = """
            {
              "sourceTimeRange": { "start": "1970-01-01T00:00:00Z", "end": "1970-01-01T00:00:10Z" },
              "environments": [ { "id": "test" } ]
            }
            """;

        given()
            .contentType("application/json").body(body)
            .when().post("/api/impact")
            .then()
            .statusCode(200)
            .body("payload.size()", is(2))
            .body("payload.assetIdentity.environmentId", containsInAnyOrder("test", "test"))
            .body("payload.find { it.assetIdentity.id == 'test-cluster' }.provider", is("BOAVIZTA"))
            .body("payload.find { it.assetIdentity.id == 'test-cluster' }.footprint.gwp", notNullValue())
            .body("payload.find { it.assetIdentity.id == 'test-cluster' }.serviceImpacts[0].assetType.name", is("tech.illuin.wombat-module.kubernetes-api"))
            .body("payload.find { it.assetIdentity.id == 'test-cluster' }.serviceImpacts[0].location.namespace", is("test-ns"));
    }

    @Test
    void getImpact_serializesKubernetesAndLlmSpecificFields()
    {
        String body = """
            {
              "sourceTimeRange": { "start": "1970-01-01T00:00:00Z", "end": "1970-01-02T00:00:00Z" },
              "environments": [ { "id": "test" } ]
            }
            """;

        given()
            .contentType("application/json").body(body)
            .when().post("/api/impact")
            .then()
            .statusCode(200)
            .body("payload.find { it.assetIdentity.id == 'test-cluster' }.serviceImpacts[0].profile", notNullValue())
            .body("payload.find { it.assetIdentity.id == 'test-cluster' }.serviceImpacts[0].profile.instanceType", is("c5.large"))
            .body("payload.find { it.assetIdentity.id == 'test-cluster' }.serviceImpacts[0].serverSpecification", notNullValue())
            .body("payload.find { it.assetIdentity.id == 'test-cluster' }.serviceImpacts[0].location.namespace", is("test-ns"))
            .body("payload.find { it.assetIdentity.id == 'test-llm' }.serviceImpacts[0].estimation", notNullValue())
            .body("payload.find { it.assetIdentity.id == 'test-llm' }.serviceImpacts[0].requestCount", notNullValue())
            .body("payload.find { it.assetIdentity.id == 'test-llm' }.serviceImpacts[0].outputTokenCount", notNullValue());
    }

    @Test
    void getImpact_filtersServicesFromCamelCaseRequest()
    {
        String body = """
            {
              "sourceTimeRange": { "start": "1970-01-01T00:00:00Z", "end": "1970-01-01T00:00:10Z" },
              "environments": [ { "id": "test", "assets": [ { "id": "test-cluster", "serviceIds": ["api"] } ] } ]
            }
            """;

        given()
            .contentType("application/json").body(body)
            .when().post("/api/impact")
            .then()
            .statusCode(200)
            .body("payload.size()", is(1))
            .body("payload[0].serviceImpacts.size()", is(1))
            .body("payload[0].serviceImpacts[0].serviceId", is("api"));
    }

    @Test
    void getTimeline_rescalesAndReturnsSeriesBuckets()
    {
        String body = """
            {
              "sourceTimeRange": { "start": "1970-01-01T00:00:00Z", "end": "1970-01-09T00:00:00Z" },
              "environments": [ { "id": "test" } ]
            }
            """;

        given()
            .contentType("application/json").body(body)
            .when().post("/api/impact/timeline")
            .then()
            .statusCode(200)
            .body("payload.stepMs", is(86_400_000))
            .body("payload.stepLabel", is("daily"))
            .body("payload.stepPlural", is("days"))
            .body("payload.bucketStarts.size()", is(8))
            .body("payload.series.size()", is(3))
            .body("payload.series.key", containsInAnyOrder("gwp", "pe", "adp"))
            .body("payload.series[0].buckets.size()", is(8));
    }

    @Test
    void getTimeline_serviceIdsChangeTheReturnedBuckets()
    {
        String allServices = """
            {
              "sourceTimeRange": { "start": "1970-01-01T00:00:00Z", "end": "1970-01-09T00:00:00Z" },
              "environments": [ { "id": "test", "assets": [ { "id": "test-cluster" } ] } ]
            }
            """;
        String oneService = """
            {
              "sourceTimeRange": { "start": "1970-01-01T00:00:00Z", "end": "1970-01-09T00:00:00Z" },
              "environments": [ { "id": "test", "assets": [ { "id": "test-cluster", "serviceIds": ["api"] } ] } ]
            }
            """;

        List<?> allBuckets = given()
            .contentType("application/json").body(allServices)
            .when().post("/api/impact/timeline")
            .then().statusCode(200)
            .extract().path("payload.series.find { it.key == 'gwp' }.buckets");
        List<?> filteredBuckets = given()
            .contentType("application/json").body(oneService)
            .when().post("/api/impact/timeline")
            .then().statusCode(200)
            .extract().path("payload.series.find { it.key == 'gwp' }.buckets");

        assertNotEquals(allBuckets, filteredBuckets);
    }

    @Test
    void getImpact_withoutActivityInRange_returnsBadRequestWithItsReason()
    {
        String body = """
            {
              "sourceTimeRange": { "start": "2000-01-01T00:00:00Z", "end": "2000-01-02T00:00:00Z" },
              "environments": [ { "id": "test", "assets": [ { "id": "test-cluster" } ] } ]
            }
            """;

        given()
            .contentType("application/json").body(body)
            .when().post("/api/impact")
            .then()
            .statusCode(400)
            .body("status.metadata.reason", is(ImpactController.NO_DATA_REASON));
    }

    @Test
    void getTimeline_withNoMatchingAssets_returnsBadRequest()
    {
        String body = """
            {
              "sourceTimeRange": { "start": "1970-01-01T00:00:00Z", "end": "1970-01-09T00:00:00Z" },
              "environments": [ { "id": "test", "assets": [ { "id": "unknown-asset" } ] } ]
            }
            """;

        given()
            .contentType("application/json").body(body)
            .when().post("/api/impact/timeline")
            .then()
            .statusCode(400);
    }

    @Test
    void getImpact_withUnknownEnvironmentId_returns400()
    {
        String body = """
            {
              "sourceTimeRange": { "start": "1970-01-01T00:00:00Z", "end": "1970-01-01T00:00:10Z" },
              "environments": [ { "id": "unknown-env" } ]
            }
            """;

        given()
            .contentType("application/json").body(body)
            .when().post("/api/impact")
            .then()
            .statusCode(400);
    }

    @Test
    void getImpact_environmentWithoutId_returns400()
    {
        String body = """
            {
              "sourceTimeRange": { "start": "1970-01-01T00:00:00Z", "end": "1970-01-01T00:00:10Z" },
              "environments": [ { "assets": [ { "id": "test-cluster" } ] } ]
            }
            """;

        given()
            .contentType("application/json").body(body)
            .when().post("/api/impact")
            .then()
            .statusCode(400);
    }

    @Test
    void getEnvironments_listsConfiguredEnvironments()
    {
        given()
            .when().get("/api/impact/environments")
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
            .when().get("/api/impact/environments/test/assets")
            .then()
            .statusCode(200)
            .body("payload.size()", is(2))
            .body("payload.type.name", containsInAnyOrder("tech.illuin.wombat-module.kubernetes-api", "tech.illuin.wombat-module.llm-static"))
            .body("payload.find { it.id == 'test-cluster' }.name", is("Test Cluster"));
    }

    @Test
    void getAssets_unknownEnvironment_returns404()
    {
        given()
            .when().get("/api/impact/environments/unknown-env/assets")
            .then()
            .statusCode(404);
    }

    @Transactional
    void emptyDatapoints()
    {
        datapointRepository.deleteAll();
    }
}
