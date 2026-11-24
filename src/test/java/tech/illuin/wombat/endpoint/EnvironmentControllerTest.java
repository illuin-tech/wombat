package tech.illuin.wombat.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConfigHistoryRepository;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
class EnvironmentControllerTest
{
    private static final String CREATED = "env-controller";

    @Inject EnvironmentRepository environments;
    @Inject AssetRepository assets;
    @Inject AssetConfigHistoryRepository history;

    @AfterEach
    void clean()
    {
        ContextControllerCleanup.clean(this.environments, this.assets, this.history, List.of(CREATED), List.of("env-controller-asset"));
    }

    @Test
    void list_returnsReconciledEnvironments()
    {
        String response = given()
            .when().get("/api/environments")
            .then()
            .statusCode(200)
            .body("payload.id", hasItem("test"))
            .body("payload.find { it.id == 'test' }.name", is("test"))
            .body("payload.find { it.id == 'test' }.uuid", notNullValue())
            .body("payload.find { it.id == 'test' }.provisioned", is(true))
            .body("payload.find { it.id == 'test' }.'createdAt'", notNullValue())
            .body("payload.find { it.id == 'test' }.'disabledAt'", nullValue())
            .body("payload.find { it.id == 'test' }.assets.id", hasItem("test-cluster"))
            .body("payload.find { it.id == 'test' }.assets.id", hasItem("test-llm"))
            .body("payload.find { it.id == 'test' }.assets.find { it.id == 'test-cluster' }.namespaces", hasItem("test-ns"))
            .body("payload.find { it.id == 'test' }.assets.find { it.id == 'test-cluster' }.environmentId", is("test"))
            .body("payload.find { it.id == 'test' }.assets.find { it.id == 'test-cluster' }.profile.'instanceType'", is("c5.large"))
            .body("payload.find { it.id == 'test' }.assets.find { it.id == 'test-cluster' }.provisioned", is(true))
            .body("payload.find { it.id == 'test' }.assets.find { it.id == 'test-cluster' }.'createdAt'", notNullValue())
            .extract().asString();
        assertFalse(response.contains("\"environment-id\""));
        assertFalse(response.contains("\"instance-type\""));
    }

    @Test
    void get_byIdAndUuid()
    {
        String uuid = given()
            .when().get("/api/environments/test")
            .then()
            .statusCode(200)
            .body("payload.id", is("test"))
            .body("payload.provisioned", is(true))
            .body("payload.assets.id", hasItem("test-llm"))
            .extract().path("payload.uuid");

        given().when().get("/api/environments/" + uuid + "/uuid").then().statusCode(200).body("payload.id", is("test"));
    }

    @Test
    void get_unknown_returns404()
    {
        given().when().get("/api/environments/does-not-exist").then().statusCode(404);
        given().when().get("/api/environments/does-not-exist/uuid").then().statusCode(404);
    }

    @Test
    void create_update_delete()
    {
        String uuid = this.create(CREATED)
            .then()
            .statusCode(201)
            .body("payload.id", is(CREATED))
            .body("payload.name", is("Env controller"))
            .body("payload.description", is("Described"))
            .body("payload.provisioned", is(false))
            .body("payload.disabledAt", nullValue())
            .extract().path("payload.uuid");

        given().contentType(ContentType.JSON)
            .body(Map.of("name", "Renamed", "disabled", true))
            .when().put("/api/environments/" + CREATED)
            .then()
            .statusCode(200)
            .body("payload.name", is("Renamed"))
            .body("payload.description", nullValue())
            .body("payload.disabledAt", notNullValue());

        given().when().get("/api/environments/" + uuid + "/uuid").then().statusCode(200).body("payload.name", is("Renamed"));

        given().when().delete("/api/environments/" + CREATED).then().statusCode(204);

        given().when().get("/api/environments/" + CREATED).then().statusCode(404);
        given().when().get("/api/environments/" + uuid + "/uuid").then().statusCode(404);
        given().when().get("/api/environments").then().statusCode(200).body("payload.id", not(hasItem(CREATED)));
        given().when().delete("/api/environments/" + CREATED).then().statusCode(404);
    }

    @Test
    void create_invalidPayload_returns400()
    {
        this.create("Not A Slug").then().statusCode(400);
        given().contentType(ContentType.JSON)
            .body(Map.of("id", CREATED))
            .when().post("/api/environments")
            .then().statusCode(400);
    }

    @Test
    void create_existing_returns409()
    {
        this.create("test").then().statusCode(409);
        this.create(CREATED).then().statusCode(201);
        this.create(CREATED).then().statusCode(409);
    }

    @Test
    void updateAndDelete_provisioned_return409()
    {
        given().contentType(ContentType.JSON)
            .body(Map.of("name", "test"))
            .when().put("/api/environments/test")
            .then().statusCode(409);
        given().when().delete("/api/environments/test").then().statusCode(409);
    }

    @Test
    void update_unknown_returns404()
    {
        given().contentType(ContentType.JSON)
            .body(Map.of("name", "Nope"))
            .when().put("/api/environments/does-not-exist")
            .then().statusCode(404);
    }

    @Test
    void delete_holdingAssets_returns409()
    {
        this.create(CREATED).then().statusCode(201);
        Map<String, Object> properties = AssetControllerTest.properties();
        given().contentType(ContentType.JSON)
            .body(AssetControllerTest.createBody("env-controller-asset", CREATED, properties))
            .when().post("/api/assets")
            .then().statusCode(201);

        given().when().delete("/api/environments/" + CREATED).then().statusCode(409);
    }

    @Test
    void history_returnsCreateEntryForReconciledAsset()
    {
        given()
            .when().get("/api/environments/test/assets/test-llm/history")
            .then()
            .statusCode(200)
            .body("payload.action", hasItem("CREATE"))
            .body("payload.find { it.action == 'CREATE' }.snapshot.id", is("test-llm"))
            .body("payload.find { it.action == 'CREATE' }.changedAt", notNullValue());
    }

    @Test
    void history_unknownEnvironment_returns404()
    {
        given()
            .when().get("/api/environments/does-not-exist/assets/whatever/history")
            .then()
            .statusCode(404);
    }

    private io.restassured.response.Response create(String id)
    {
        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put("name", "Env controller");
        body.put("description", "Described");
        return given().contentType(ContentType.JSON).body(body).when().post("/api/environments");
    }
}
