package tech.illuin.wombat.endpoint;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.simulation.persistence.SimulationAssetRepository;
import tech.illuin.wombat.engine.simulation.persistence.SimulationRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test profile provisions the environment {@code test}, holding the assets {@code test-llm} and
 * {@code test-cluster}, which the simulations copy.
 */
@QuarkusTest
class SimulationSandboxControllerTest
{
    private static final String CREATED = "sim-controller";
    private static final String DUPLICATED = "sim-controller-copy";

    @Inject SimulationRepository simulations;
    @Inject SimulationAssetRepository simulationAssets;

    @AfterEach
    void clean()
    {
        clean(this.simulations, this.simulationAssets, List.of(CREATED, DUPLICATED));
    }

    @Test
    void create_update_delete()
    {
        this.create(CREATED, Map.of())
            .then()
            .statusCode(201)
            .body("payload.id", is(CREATED))
            .body("payload.name", is("Sim controller"))
            .body("payload.description", is("Described"))
            .body("payload.referenceWindow", is("MONTH"))
            .body("payload.parentEnvironmentId", nullValue())
            .body("payload.createdAt", notNullValue())
            .body("payload.assets", empty());

        given().contentType(ContentType.JSON)
            .body(Map.of("name", "Renamed", "notes", "Assumes **10k** users", "referenceWindow", "YEAR"))
            .when().put("/api/simulation/sandbox/" + CREATED)
            .then()
            .statusCode(200)
            .body("payload.name", is("Renamed"))
            .body("payload.description", nullValue())
            .body("payload.notes", is("Assumes **10k** users"))
            .body("payload.referenceWindow", is("YEAR"));

        given().when().get("/api/simulation/sandbox/" + CREATED).then().statusCode(200).body("payload.name", is("Renamed"));
        given().when().get("/api/simulation/sandbox").then().statusCode(200)
            .body("payload.find { it.id == '" + CREATED + "' }.assetCount", is(0))
            .body("payload.find { it.id == '" + CREATED + "' }.notes", nullValue());

        given().when().delete("/api/simulation/sandbox/" + CREATED).then().statusCode(204);

        given().when().get("/api/simulation/sandbox/" + CREATED).then().statusCode(404);
        given().when().get("/api/simulation/sandbox").then().statusCode(200).body("payload.id", not(hasItem(CREATED)));
        given().when().delete("/api/simulation/sandbox/" + CREATED).then().statusCode(404);
    }

    @Test
    void create_copiesThePickedAssetsOfTheParentEnvironment()
    {
        this.create(CREATED, Map.of("parentEnvironmentId", "test", "assetIds", List.of("test-llm")))
            .then()
            .statusCode(201)
            .body("payload.parentEnvironmentId", is("test"))
            .body("payload.parentEnvironmentName", is("test"))
            .body("payload.assets", hasSize(1))
            .body("payload.assets[0].id", is("test-llm"))
            .body("payload.assets[0].simulationId", is(CREATED))
            .body("payload.assets[0].environmentId", is("simulation:" + CREATED))
            .body("payload.assets[0].name", is("test-llm"))
            .body("payload.assets[0].parentAssetId", is("test-llm"))
            .body("payload.assets[0].parentEnvironmentId", is("test"))
            .body("payload.assets[0].parentStatus", is("in-sync"))
            .body("payload.assets[0].syncedAt", notNullValue())
            .body("payload.assets[0].profile.models[0].model", is("mistral-large-latest"));

        given().when().get("/api/simulation/sandbox").then().statusCode(200)
            .body("payload.find { it.id == '" + CREATED + "' }.assetCount", is(1))
            .body("payload.find { it.id == '" + CREATED + "' }.parentEnvironmentName", is("test"));
    }

    @Test
    void create_isAllOrNothing()
    {
        this.create(CREATED, Map.of("parentEnvironmentId", "test", "assetIds", List.of("test-llm", "does-not-exist"))).then().statusCode(404);

        given().when().get("/api/simulation/sandbox/" + CREATED).then().statusCode(404);
    }

    @Test
    void create_invalidPayload_returns400()
    {
        this.create("Not A Slug", Map.of()).then().statusCode(400);
        this.create(CREATED, Map.of("assetIds", List.of("test-llm"))).then().statusCode(400);
        this.create(CREATED, Map.of("referenceWindow", "FORTNIGHT")).then().statusCode(400);
        given().contentType(ContentType.JSON).body(Map.of("id", CREATED)).when().post("/api/simulation/sandbox").then().statusCode(400);
    }

    @Test
    void create_unknownParent_returns404()
    {
        this.create(CREATED, Map.of("parentEnvironmentId", "does-not-exist")).then().statusCode(404);
        this.create(CREATED, Map.of("parentEnvironmentId", "test", "assetIds", List.of("does-not-exist"))).then().statusCode(404);
    }

    @Test
    void create_existing_returns409()
    {
        this.create(CREATED, Map.of()).then().statusCode(201);
        this.create(CREATED, Map.of()).then().statusCode(409);
    }

    @Test
    void update_unknown_returns404()
    {
        given().contentType(ContentType.JSON)
            .body(Map.of("name", "Nope", "referenceWindow", "DAY"))
            .when().put("/api/simulation/sandbox/does-not-exist")
            .then().statusCode(404);
    }

    @Test
    void duplicate_keepsTheAssets_theirIdsAndParents()
    {
        this.create(CREATED, Map.of("parentEnvironmentId", "test", "assetIds", List.of("test-llm", "test-cluster"), "notes", "Noted")).then().statusCode(201);

        given().contentType(ContentType.JSON)
            .body(Map.of("id", DUPLICATED, "name", "Sim controller (copy)"))
            .when().post("/api/simulation/sandbox/" + CREATED + "/duplicate")
            .then()
            .statusCode(201)
            .body("payload.id", is(DUPLICATED))
            .body("payload.name", is("Sim controller (copy)"))
            .body("payload.description", is("Described"))
            .body("payload.notes", is("Noted"))
            .body("payload.parentEnvironmentId", is("test"))
            .body("payload.assets.id", is(List.of("test-cluster", "test-llm")))
            .body("payload.assets.environmentId", is(List.of("simulation:" + DUPLICATED, "simulation:" + DUPLICATED)))
            .body("payload.assets.parentAssetId", is(List.of("test-cluster", "test-llm")))
            .body("payload.assets.parentStatus", is(List.of("in-sync", "in-sync")));

        given().contentType(ContentType.JSON)
            .body(Map.of("id", DUPLICATED, "name", "Again"))
            .when().post("/api/simulation/sandbox/" + CREATED + "/duplicate")
            .then().statusCode(409);
        given().contentType(ContentType.JSON)
            .body(Map.of("id", "sim-controller-other", "name", "Nope"))
            .when().post("/api/simulation/sandbox/does-not-exist/duplicate")
            .then().statusCode(404);
    }

    @Test
    void delete_removesTheAssets()
    {
        this.create(CREATED, Map.of("parentEnvironmentId", "test", "assetIds", List.of("test-llm"))).then().statusCode(201);

        given().when().delete("/api/simulation/sandbox/" + CREATED).then().statusCode(204);

        /* SQLite does not enforce the foreign key's cascade: the service deletes them */
        assertTrue(QuarkusTransaction.requiringNew().call(() -> this.simulationAssets.findBySimulation(CREATED)).isEmpty());
        this.create(CREATED, Map.of()).then().statusCode(201).body("payload.assets", empty());
    }

    @Test
    void openApi_describesTheSandbox_andTheEvaluationsAsServed()
    {
        given().queryParam("format", "json")
            .when().get("/api/openapi")
            .then()
            .statusCode(200)
            .body("paths.'/api/simulation/sandbox/{id}'.keySet()", hasItems("get", "put", "delete"))
            .body("paths.'/api/simulation/sandbox/{id}/assets/{assetId}/sync'.post.responses.keySet()", hasItems("200", "404", "409"))
            .body("paths.'/api/simulation/sandbox/{id}/evaluation'.post.requestBody.required", is(false))
            .body("components.schemas.SimulationEvaluation.properties.keySet()", hasItems("window", "factors", "totals", "assets"))
            /* A result is an impact or a cost, told apart by the type the mapper serves */
            .body("components.schemas.AssetEvaluation.'$ref'", is("#/components/schemas/AssetEvaluationSchema"))
            .body("components.schemas.AssetImpact.properties.type.enum", contains("IMPACT"))
            .body("components.schemas.AssetCost.required", hasItem("type"));
    }

    private io.restassured.response.Response create(String id, Map<String, Object> extra)
    {
        Map<String, Object> body = new HashMap<>(extra);
        body.put("id", id);
        body.put("name", "Sim controller");
        body.put("description", "Described");
        return given().contentType(ContentType.JSON).body(body).when().post("/api/simulation/sandbox");
    }

    /** Removes the simulations a test created through the API, with their assets. */
    static void clean(SimulationRepository simulations, SimulationAssetRepository simulationAssets, List<String> simulationIds)
    {
        QuarkusTransaction.requiringNew().run(() -> {
            for (String id : simulationIds)
            {
                simulationAssets.deleteBySimulation(id);
                simulations.findByIdOptional(id).ifPresent(simulations::delete);
            }
        });
    }
}
