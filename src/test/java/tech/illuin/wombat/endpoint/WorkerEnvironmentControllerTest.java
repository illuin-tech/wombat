package tech.illuin.wombat.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.context.WombatContextProvider;
import tech.illuin.wombat.engine.context.asset.persistence.AssetEntity;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentRepository;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A worker whose environments are also declared through its API, e.g. by a sidecar listing the namespaces of its cluster,
 * on top of those of its file ({@code monitored-environments-test.yaml}: asset {@code test-cluster} in environment {@code test}).
 */
@QuarkusTest
@TestProfile(WorkerEnvironmentControllerTest.ApiDeclaredWorkerProfile.class)
class WorkerEnvironmentControllerTest
{
    @Inject
    AssetRepository assets;

    @Inject
    EnvironmentRepository environments;

    @Inject
    WombatContextProvider context;

    @Test
    void declaration_isSampled_andReplacedByTheNextOne()
    {
        this.declare(namespaces("ns-a", "ns-b"))
            .statusCode(200)
            .body("payload.created", equalTo(2));

        assertTrue(this.sampledAssetIds().containsAll(Set.of("ns-a-cluster", "ns-b-cluster")));
        given().when().get("/api/worker/environments").then()
            .statusCode(200)
            .body("payload.environments", hasKey("ns-a"))
            .body("payload.environments", hasKey("ns-b"));

        this.declare(namespaces("ns-a"))
            .statusCode(200)
            .body("payload.created", equalTo(0))
            .body("payload.deleted", equalTo(1));

        assertNull(this.asset("ns-a-cluster").deletedAt);
        assertNotNull(this.asset("ns-b-cluster").deletedAt);
        assertFalse(this.sampledAssetIds().contains("ns-b-cluster"));
        given().when().get("/api/worker/environments").then()
            .statusCode(200)
            .body("payload.environments", not(hasKey("ns-b")))
            .body("payload.environments", not(hasKey("test")));
    }

    /* A declaration only replaces its own assets: the file's are kept, even in an environment both declare. */
    @Test
    void declaration_keepsTheFileAssets()
    {
        String body = """
            {"environments": {"test": {"id": "test", "assets": [%s]}}}""".formatted(asset("test", "test-api-cluster", "test-kubeconfig"));

        this.declare(body).statusCode(200);
        this.declare(namespaces("ns-c")).statusCode(200);

        assertNull(this.asset("test-cluster").deletedAt);
        assertNotNull(this.asset("test-api-cluster").deletedAt);
        assertTrue(this.sampledAssetIds().contains("test-cluster"));
        assertNull(this.environments.findById("test").deletedAt);
    }

    @Test
    void assetDeclaredByTheFile_isAConflict()
    {
        String body = """
            {"environments": {"test": {"id": "test", "assets": [%s]}}}""".formatted(asset("test", "test-cluster", "test-kubeconfig"));

        this.declare(body).statusCode(409);

        assertNull(this.asset("test-cluster").owner);
    }

    @Test
    void assetMissingASecret_isRejected_andNothingApplied()
    {
        String body = """
            {"environments": {
              "ns-secretless": %s,
              "ns-other": %s
            }}""".formatted(namespace("ns-secretless", "missing-kubeconfig"), namespace("ns-other", "test-kubeconfig"));

        this.declare(body).statusCode(400);

        assertNull(this.asset("ns-secretless-cluster"));
        assertNull(this.asset("ns-other-cluster"));
    }

    @Test
    void assetOfAnUnknownType_isRejected()
    {
        String body = """
            {"environments": {"ns-unknown": {"id": "ns-unknown", "assets": [
              {"type": "tech.illuin.wombat-module.unknown", "id": "ns-unknown-asset", "environmentId": "ns-unknown"}
            ]}}}""";

        this.declare(body).statusCode(400);

        assertNull(this.asset("ns-unknown-asset"));
    }

    /* Only the declaration endpoint is opened: the worker still serves no other API. */
    @Test
    void otherApis_areStillNotServed()
    {
        given().when().get("/api/environments").then().statusCode(404);
        given().when().get("/api/assets").then().statusCode(404);
        given().contentType(ContentType.JSON).body("[]").when().post("/api/workers/east/metrics").then().statusCode(404);
    }

    private ValidatableResponse declare(String body)
    {
        return given().contentType(ContentType.JSON).body(body).when().put("/api/worker/environments").then();
    }

    private Set<String> sampledAssetIds()
    {
        return this.context.provide().assets().stream()
            .map(Asset::identity)
            .map(AssetIdentity::id)
            .collect(Collectors.toSet());
    }

    private AssetEntity asset(String id)
    {
        return this.assets.findById(id);
    }

    private static String namespaces(String... namespaces)
    {
        String environments = Arrays.stream(namespaces)
            .map(namespace -> "\"" + namespace + "\": " + namespace(namespace, "test-kubeconfig"))
            .collect(Collectors.joining(",\n"));
        return "{\"environments\": {\n" + environments + "\n}}";
    }

    /** One environment per namespace, sampled by a Kubernetes asset limited to it. Asset properties are camelCase in JSON, as everywhere in the API. */
    private static String namespace(String namespace, String kubeconfigKey)
    {
        return """
            {"id": "%s", "assets": [%s]}""".formatted(namespace, asset(namespace, namespace + "-cluster", kubeconfigKey));
    }

    private static String asset(String namespace, String id, String kubeconfigKey)
    {
        return """
            {
              "type": "tech.illuin.wombat-module.kubernetes-api",
              "id": "%2$s",
              "environmentId": "%1$s",
              "kubeconfigKey": "%3$s",
              "namespaces": ["%1$s"],
              "profile": {"provider": "aws", "instanceType": "c5.large", "location": "FRA", "lifespan": 43800}
            }""".formatted(namespace, id, kubeconfigKey);
    }

    public static class ApiDeclaredWorkerProfile implements QuarkusTestProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            return Map.of(
                "wombat.mode", "worker",
                "wombat.worker.id", "test-worker",
                "wombat.worker.aggregator.url", "http://localhost:0",
                "wombat.worker.aggregator.api-key", "unused",
                "wombat.worker.environments.api", "true"
            );
        }
    }
}
