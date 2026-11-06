package tech.illuin.wombat.endpoint;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.engine.impact.llm.LLMModelMetricRepository;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The metrics of an asset whose module the aggregator disabled are skipped, while the others of the request are saved. */
@QuarkusTest
@TestProfile(DisabledModuleProfile.class)
class MetricControllerDisabledModuleTest
{
    @Inject
    KubernetesMetricRepository kubernetes;

    @Inject
    LLMModelMetricRepository llm;

    @BeforeEach
    void setUp()
    {
        QuarkusTransaction.requiringNew().run(() -> {
            this.kubernetes.delete("assetId", "east.pushed-cluster");
            this.llm.delete("assetId", "east.pushed-llm");
        });
    }

    @Test
    void push_skipsTheMetricsOfADisabledModule_reportsThemInTheStatus_andSavesTheOthers()
    {
        given().contentType(ContentType.JSON).body("""
            [
              {"asset": {"id": "pushed-cluster", "environmentId": "remote", "name": "Pushed cluster", "type": "tech.illuin.wombat-module.kubernetes-api"}, "window": {"start": "2023-11-14T22:15:00Z", "end": "2023-11-14T22:20:00Z"}, "data": {"family": "KUBERNETES_CONTAINER", "serviceId": "api", "cluster": "cluster", "namespace": "namespace", "pod": "pod", "cpuNanocores": 100.0, "ramBytes": 2048.0}},
              {"asset": {"id": "pushed-cluster", "environmentId": "remote", "name": "Pushed cluster", "type": "tech.illuin.wombat-module.kubernetes-api"}, "window": {"start": "2023-11-14T22:15:00Z", "end": "2023-11-14T22:20:00Z"}, "data": {"family": "KUBERNETES_CONTAINER", "serviceId": "worker", "cluster": "cluster", "namespace": "namespace", "pod": "pod", "cpuNanocores": 50.0, "ramBytes": 1024.0}},
              {"asset": {"id": "pushed-llm", "environmentId": "remote", "name": "Pushed LLM", "type": "tech.illuin.wombat-module.llm-static"}, "window": {"start": "2023-11-14T22:15:00Z", "end": "2023-11-14T22:20:00Z"}, "data": {"family": "LLM", "serviceId": "chat", "provider": "mistralai", "model": "mistral-large-latest", "location": "FRA", "outputTokens": 10, "requests": 1}}
            ]
            """)
            .when().post("/api/workers/east/metrics")
            .then().statusCode(200)
            .body("payload", is(1))
            .body("status.message", is("Saved 1 metric(s), skipped 2"))
            .body("status.metadata.errors", hasSize(1))
            .body("status.metadata.errors[0].assetId", is("pushed-cluster"))
            .body("status.metadata.errors[0].assetType", is("tech.illuin.wombat-module.kubernetes-api"))
            .body("status.metadata.errors[0].reason", is("No active module for asset type tech.illuin.wombat-module.kubernetes-api"))
            .body("status.metadata.errors[0].count", is(2));

        assertEquals(0, this.kubernetes.count("assetId", "east.pushed-cluster"));
        assertEquals(1, this.llm.count("assetId", "east.pushed-llm"));
    }
}
