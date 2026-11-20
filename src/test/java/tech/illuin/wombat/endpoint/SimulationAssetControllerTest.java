package tech.illuin.wombat.endpoint;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.context.asset.InvalidAssetException;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConfigHistoryRepository;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentRepository;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.engine.simulation.SimulationAssetService;
import tech.illuin.wombat.engine.simulation.persistence.SimulationAssetRepository;
import tech.illuin.wombat.engine.simulation.persistence.SimulationRepository;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Copies either the provisioned {@code test-llm}, or {@code sim-asset-parent}: an asset created here, which can be
 * edited and deleted.
 */
@QuarkusTest
class SimulationAssetControllerTest
{
    private static final String SIMULATION = "sim-asset-controller";
    private static final String OTHER_SIMULATION = "sim-asset-controller-other";
    private static final String ENVIRONMENT = "sim-asset-env";
    private static final String PARENT = "sim-asset-parent";
    private static final String TYPE = LLMStaticModule.TYPE.name();

    @Inject SimulationRepository simulations;
    @Inject SimulationAssetRepository simulationAssets;
    @Inject EnvironmentRepository environments;
    @Inject AssetRepository assets;
    @Inject AssetConfigHistoryRepository history;
    @Inject SimulationAssetService service;
    @Inject JsonMapper mapper;

    @BeforeEach
    void setUp()
    {
        this.clean();
        for (String id : List.of(SIMULATION, OTHER_SIMULATION))
            given().contentType(ContentType.JSON).body(Map.of("id", id, "name", id)).when().post("/api/simulations").then().statusCode(201);
    }

    @AfterEach
    void clean()
    {
        SimulationsControllerTest.clean(this.simulations, this.simulationAssets, List.of(SIMULATION, OTHER_SIMULATION));
        ContextControllerCleanup.clean(this.environments, this.assets, this.history, List.of(ENVIRONMENT), List.of(PARENT));
    }

    @Test
    void fromScratch_create_update_delete()
    {
        this.createFromScratch("scratch")
            .then()
            .statusCode(201)
            .body("payload.id", is("scratch"))
            .body("payload.simulationId", is(SIMULATION))
            .body("payload.environmentId", is("simulation:" + SIMULATION))
            .body("payload.type", is(TYPE))
            .body("payload.name", is("From scratch"))
            .body("payload.notes", is("Noted"))
            .body("payload.parentAssetId", nullValue())
            .body("payload.parentStatus", nullValue())
            .body("payload.syncedAt", nullValue());

        Map<String, Object> properties = this.properties("mistral-small-latest");
        given().contentType(ContentType.JSON)
            .body(Map.of("name", "Renamed", "properties", properties))
            .when().put(this.path("scratch"))
            .then()
            .statusCode(200)
            .body("payload.name", is("Renamed"))
            .body("payload.notes", nullValue())
            .body("payload.profile.models[0].model", is("mistral-small-latest"));

        given().when().get(this.path("scratch")).then().statusCode(200).body("payload.name", is("Renamed"));
        given().when().get(this.path("")).then().statusCode(200).body("payload.id", is(List.of("scratch")));

        given().when().delete(this.path("scratch")).then().statusCode(204);
        given().when().get(this.path("scratch")).then().statusCode(404);
        given().when().delete(this.path("scratch")).then().statusCode(404);
    }

    @Test
    void fromScratch_invalid_returns400()
    {
        Map<String, Object> noId = this.scratchBody(null);
        given().contentType(ContentType.JSON).body(noId).when().post(this.path("")).then().statusCode(400);

        Map<String, Object> unknownType = this.scratchBody("scratch");
        unknownType.put("type", "tech.illuin.unknown");
        given().contentType(ContentType.JSON).body(unknownType).when().post(this.path("")).then().statusCode(400);

        Map<String, Object> invalidProperties = this.scratchBody("scratch");
        invalidProperties.put("properties", Map.of("profile", Map.of("models", "not-a-list")));
        given().contentType(ContentType.JSON).body(invalidProperties).when().post(this.path("")).then().statusCode(400);

        Map<String, Object> badId = this.scratchBody("Not A Slug");
        given().contentType(ContentType.JSON).body(badId).when().post(this.path("")).then().statusCode(400);
    }

    @Test
    void fromScratch_aStatelessSimulationTypeIsRefused()
    {
        ObjectNode properties = this.mapper.valueToTree(Map.of(
            "profile", Map.of("provider", "mistralai", "model", "mistral-large-latest", "location", "FRA"),
            "activity", Map.of(
                "regime", "MODELED",
                "serviceIds", List.of("mistral-large-latest"),
                "range", Map.of("start", "2026-01-01T00:00:00Z", "end", "2026-01-02T00:00:00Z"),
                "services", Map.of()
            )
        ));

        /* Through the service: the API answers 400 without a message, as for any invalid definition */
        InvalidAssetException e = assertThrows(InvalidAssetException.class, () -> this.service.create(
            SIMULATION, "simulated", "Simulated", null, null, null, LLMSimulatedModule.TYPE.name(), properties
        ));
        assertTrue(e.getMessage().contains("stateless"), e.getMessage());
    }

    @Test
    void anIdIsUniqueWithinItsSimulationOnly()
    {
        this.createFromScratch("scratch").then().statusCode(201);
        this.createFromScratch("scratch").then().statusCode(409);

        given().contentType(ContentType.JSON).body(this.scratchBody("scratch")).when().post("/api/simulations/" + OTHER_SIMULATION + "/assets").then().statusCode(201);
    }

    @Test
    void copy_takesTheParentsTypeAndProperties_underAnIdOfItsOwn()
    {
        this.copy(Map.of("parentAssetId", "test-llm"))
            .then()
            .statusCode(201)
            .body("payload.id", is("test-llm"))
            .body("payload.environmentId", is("simulation:" + SIMULATION))
            .body("payload.type", is(TYPE))
            .body("payload.parentAssetId", is("test-llm"))
            .body("payload.parentEnvironmentId", is("test"))
            .body("payload.parentStatus", is("in-sync"))
            .body("payload.syncedAt", notNullValue())
            .body("payload.profile.models[0].model", is("mistral-large-latest"));

        this.copy(Map.of("parentAssetId", "test-llm")).then().statusCode(201).body("payload.id", is("test-llm-2"));
        this.copy(Map.of("parentAssetId", "test-llm", "id", "test-llm")).then().statusCode(409);
    }

    @Test
    void copy_editedBeforeItsFirstSave_hasDiverged()
    {
        this.copy(Map.of("parentAssetId", "test-llm", "properties", this.properties("mistral-small-latest")))
            .then()
            .statusCode(201)
            .body("payload.parentStatus", is("diverged"))
            .body("payload.profile.models[0].model", is("mistral-small-latest"));
    }

    @Test
    void copy_invalid()
    {
        this.copy(Map.of("parentAssetId", "does-not-exist")).then().statusCode(404);
        this.copy(Map.of("parentAssetId", "test-llm", "type", "tech.illuin.wombat-module.kubernetes-api")).then().statusCode(400);
        given().contentType(ContentType.JSON).body(Map.of("name", "Nope", "parentAssetId", "test-llm"))
            .when().post("/api/simulations/does-not-exist/assets")
            .then().statusCode(404);
    }

    @Test
    void parentStatus_followsEitherSide_andSyncTakesTheParentBack()
    {
        this.createParent();
        this.copy(Map.of("parentAssetId", PARENT)).then().statusCode(201).body("payload.parentStatus", is("in-sync"));

        given().contentType(ContentType.JSON)
            .body(Map.of("name", "Copy", "properties", this.properties("mistral-small-latest")))
            .when().put(this.path(PARENT))
            .then().statusCode(200).body("payload.parentStatus", is("diverged"));

        String syncedAt = given().when().post(this.path(PARENT + "/sync"))
            .then()
            .statusCode(200)
            .body("payload.parentStatus", is("in-sync"))
            .body("payload.profile.models[0].model", is("mistral-large-latest"))
            .extract().path("payload.syncedAt");

        given().contentType(ContentType.JSON)
            .body(Map.of("name", "Parent", "properties", this.properties("open-mistral-nemo")))
            .when().put("/api/assets/" + PARENT)
            .then().statusCode(200);
        given().when().get(this.path(PARENT)).then().statusCode(200)
            .body("payload.parentStatus", is("diverged"))
            .body("payload.syncedAt", is(syncedAt));

        given().when().delete("/api/assets/" + PARENT).then().statusCode(204);
        given().when().get(this.path(PARENT)).then().statusCode(200)
            .body("payload.parentStatus", is("parent-removed"))
            .body("payload.profile.models[0].model", is("mistral-large-latest"));
        given().when().post(this.path(PARENT + "/sync")).then().statusCode(409);
    }

    @Test
    void sync_withoutParent_returns409()
    {
        this.createFromScratch("scratch").then().statusCode(201);

        given().when().post(this.path("scratch/sync")).then().statusCode(409);
        given().when().post(this.path("does-not-exist/sync")).then().statusCode(404);
    }

    @Test
    void unknownSimulation_returns404()
    {
        given().when().get("/api/simulations/does-not-exist/assets").then().statusCode(404);
        given().when().get("/api/simulations/does-not-exist/assets/test-llm").then().statusCode(404);
        given().contentType(ContentType.JSON).body(this.scratchBody("scratch")).when().post("/api/simulations/does-not-exist/assets").then().statusCode(404);
    }

    @Test
    void simulationAssets_neverReachTheMonitoredContext()
    {
        this.copy(Map.of("parentAssetId", "test-llm", "id", "sim-only")).then().statusCode(201);

        given().when().get("/api/assets").then().statusCode(200).body("payload.id", not(hasItem("sim-only")));
        given().when().get("/api/environments").then().statusCode(200).body("payload.id", not(hasItem("simulation:" + SIMULATION)));
    }

    private io.restassured.response.Response createFromScratch(String id)
    {
        return given().contentType(ContentType.JSON).body(this.scratchBody(id)).when().post(this.path(""));
    }

    private io.restassured.response.Response copy(Map<String, Object> extra)
    {
        Map<String, Object> body = new HashMap<>(extra);
        body.put("name", "Copy");
        return given().contentType(ContentType.JSON).body(body).when().post(this.path(""));
    }

    private void createParent()
    {
        given().contentType(ContentType.JSON).body(Map.of("id", ENVIRONMENT, "name", "Sim asset env")).when().post("/api/environments").then().statusCode(201);
        given().contentType(ContentType.JSON)
            .body(AssetControllerTest.createBody(PARENT, ENVIRONMENT, this.properties("mistral-large-latest")))
            .when().post("/api/assets")
            .then().statusCode(201);
    }

    private Map<String, Object> scratchBody(String id)
    {
        Map<String, Object> body = new HashMap<>();
        if (id != null)
            body.put("id", id);
        body.put("name", "From scratch");
        body.put("notes", "Noted");
        body.put("type", TYPE);
        body.put("properties", this.properties("mistral-large-latest"));
        return body;
    }

    /** The properties of the provisioned llm-static asset, with another model. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> properties(String model)
    {
        Map<String, Object> properties = AssetControllerTest.properties();
        Map<String, Object> profile = new HashMap<>((Map<String, Object>) properties.get("profile"));
        List<Map<String, Object>> models = (List<Map<String, Object>>) profile.get("models");
        Map<String, Object> first = new HashMap<>(models.getFirst());
        first.put("model", model);
        profile.put("models", List.of(first));
        properties.put("profile", profile);
        return properties;
    }

    private String path(String suffix)
    {
        return "/api/simulations/" + SIMULATION + "/assets" + (suffix.isEmpty() ? "" : "/" + suffix);
    }
}
