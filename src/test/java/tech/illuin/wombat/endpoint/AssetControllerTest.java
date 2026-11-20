package tech.illuin.wombat.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.context.WombatContextProvider;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConfigHistoryRepository;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentRepository;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test profile provisions the environment {@code test}, holding the asset {@code test-llm}.
 */
@QuarkusTest
class AssetControllerTest
{
    private static final String ENVIRONMENT = "asset-controller-env";
    private static final String ASSET = "asset-controller-llm";

    @Inject EnvironmentRepository environments;
    @Inject AssetRepository assets;
    @Inject AssetConfigHistoryRepository history;
    @Inject WombatContextProvider context;

    @BeforeEach
    void setUp()
    {
        this.clean();
        given().contentType(ContentType.JSON)
            .body(Map.of("id", ENVIRONMENT, "name", "Asset controller"))
            .when().post("/api/environments")
            .then().statusCode(201);
    }

    @AfterEach
    void clean()
    {
        ContextControllerCleanup.clean(this.environments, this.assets, this.history, List.of(ENVIRONMENT), List.of(ASSET));
    }

    @Test
    void get_provisioned_byIdAndUuid()
    {
        String uuid = given()
            .when().get("/api/assets/test-llm")
            .then()
            .statusCode(200)
            .body("payload.id", is("test-llm"))
            .body("payload.environmentId", is("test"))
            .body("payload.type", is(LLMStaticModule.TYPE.name()))
            .body("payload.provisioned", is(true))
            .extract().path("payload.uuid");

        given().when().get("/api/assets/" + uuid + "/uuid").then().statusCode(200).body("payload.id", is("test-llm"));
    }

    @Test
    void get_unknown_returns404()
    {
        given().when().get("/api/assets/does-not-exist").then().statusCode(404);
        given().when().get("/api/assets/does-not-exist/uuid").then().statusCode(404);
    }

    @Test
    void list_filtersByEnvironment()
    {
        this.create(ASSET, ENVIRONMENT).then().statusCode(201);

        given().when().get("/api/assets").then().statusCode(200).body("payload.id", hasItems("test-llm", ASSET));
        given().queryParam("environmentId", ENVIRONMENT)
            .when().get("/api/assets")
            .then().statusCode(200).body("payload.id", is(List.of(ASSET)));
        given().queryParam("environmentId", "does-not-exist").when().get("/api/assets").then().statusCode(404);
    }

    @Test
    void create_update_delete()
    {
        String uuid = this.create(ASSET, ENVIRONMENT)
            .then()
            .statusCode(201)
            .body("payload.id", is(ASSET))
            .body("payload.environmentId", is(ENVIRONMENT))
            .body("payload.name", is("Asset controller LLM"))
            .body("payload.description", is("Described"))
            .body("payload.type", is(LLMStaticModule.TYPE.name()))
            .body("payload.provisioned", is(false))
            .body("payload.profile", notNullValue())
            .extract().path("payload.uuid");
        assertTrue(this.activeAssets().contains(ASSET));

        Map<String, Object> update = new HashMap<>();
        update.put("name", "Renamed");
        update.put("disabled", true);
        update.put("properties", properties());
        given().contentType(ContentType.JSON)
            .body(update)
            .when().put("/api/assets/" + ASSET)
            .then()
            .statusCode(200)
            .body("payload.name", is("Renamed"))
            .body("payload.description", nullValue())
            .body("payload.disabledAt", notNullValue());

        // Disabled: still served, but skipped by processing.
        given().when().get("/api/assets/" + uuid + "/uuid").then().statusCode(200).body("payload.name", is("Renamed"));
        assertFalse(this.activeAssets().contains(ASSET));

        given().when().delete("/api/assets/" + ASSET).then().statusCode(204);

        given().when().get("/api/assets/" + ASSET).then().statusCode(404);
        given().when().get("/api/assets").then().statusCode(200).body("payload.id", not(hasItem(ASSET)));
        given().when().get("/api/environments/" + ENVIRONMENT + "/assets/" + ASSET + "/history")
            .then().statusCode(200).body("payload.action", is(List.of("CREATE", "UPDATE", "DELETE")));
    }

    @Test
    void create_inAProvisionedEnvironment()
    {
        this.create(ASSET, "test").then().statusCode(201).body("payload.environmentId", is("test"));
    }

    @Test
    void create_invalidPayload_returns400()
    {
        this.create("Not A Slug", ENVIRONMENT).then().statusCode(400)
            .body("status.message", startsWith("Invalid request: id must only contain"))
            .body("status.metadata.violations[0].field", is("id"));

        Map<String, Object> missingProperties = createBody(ASSET, ENVIRONMENT, properties());
        missingProperties.remove("properties");
        given().contentType(ContentType.JSON).body(missingProperties).when().post("/api/assets").then().statusCode(400);

        Map<String, Object> unknownType = createBody(ASSET, ENVIRONMENT, properties());
        unknownType.put("type", "tech.illuin.unknown");
        given().contentType(ContentType.JSON).body(unknownType).when().post("/api/assets").then().statusCode(400);

        Map<String, Object> invalidProperties = createBody(ASSET, ENVIRONMENT, Map.of("profile", Map.of("models", "not-a-list")));
        given().contentType(ContentType.JSON).body(invalidProperties).when().post("/api/assets").then().statusCode(400);
    }

    @Test
    void create_unknownEnvironment_returns404()
    {
        this.create(ASSET, "does-not-exist").then().statusCode(404);
    }

    @Test
    void create_existing_returns409()
    {
        this.create("test-llm", ENVIRONMENT).then().statusCode(409);
        this.create(ASSET, ENVIRONMENT).then().statusCode(201);
        this.create(ASSET, ENVIRONMENT).then().statusCode(409);
    }

    @Test
    void updateAndDelete_provisioned_return409()
    {
        given().contentType(ContentType.JSON)
            .body(Map.of("name", "Test LLM", "properties", properties()))
            .when().put("/api/assets/test-llm")
            .then().statusCode(409);
        given().when().delete("/api/assets/test-llm").then().statusCode(409);
    }

    @Test
    void update_unknown_returns404()
    {
        given().contentType(ContentType.JSON)
            .body(Map.of("name", "Nope", "properties", properties()))
            .when().put("/api/assets/does-not-exist")
            .then().statusCode(404);
    }

    private io.restassured.response.Response create(String id, String environmentId)
    {
        return given().contentType(ContentType.JSON).body(createBody(id, environmentId, properties())).when().post("/api/assets");
    }

    static Map<String, Object> createBody(String id, String environmentId, Map<String, Object> properties)
    {
        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put("environmentId", environmentId);
        body.put("name", "Asset controller LLM");
        body.put("type", LLMStaticModule.TYPE.name());
        body.put("description", "Described");
        body.put("properties", properties);
        return body;
    }

    /** The type-specific properties of the provisioned llm-static asset, as the API serves them. */
    static Map<String, Object> properties()
    {
        Map<String, Object> asset = new HashMap<>(given().when().get("/api/assets/test-llm").then().statusCode(200).extract().path("payload"));
        for (String key : List.of("id", "environmentId", "name", "type", "uuid", "owner", "description", "provisioned", "createdAt", "updatedAt", "disabledAt", "deletedAt"))
            asset.remove(key);
        return asset;
    }

    private List<String> activeAssets()
    {
        return this.context.provide().environments().stream()
            .flatMap(environment -> environment.assets().stream())
            .map(asset -> asset.identity().id())
            .toList();
    }
}
