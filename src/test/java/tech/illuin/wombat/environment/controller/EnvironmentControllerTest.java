package tech.illuin.wombat.environment.controller;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class EnvironmentControllerTest
{

    @Test
    void list_returnsReconciledEnvironments()
    {
        given()
            .when().get("/environments")
            .then()
            .statusCode(200)
            .body("id", hasItem("test"))
            .body("find { it.id == 'test' }.name", is("test"))
            .body("find { it.id == 'test' }.uuid", notNullValue())
            .body("find { it.id == 'test' }.'created-at'", notNullValue())
            .body("find { it.id == 'test' }.'disabled-at'", nullValue())
            .body("find { it.id == 'test' }.assets.id", hasItem("test-cluster"))
            .body("find { it.id == 'test' }.assets.id", hasItem("test-llm"))
            .body("find { it.id == 'test' }.assets.find { it.id == 'test-cluster' }.namespace", is("test-ns"))
            .body("find { it.id == 'test' }.assets.find { it.id == 'test-cluster' }.profile.'instance-type'", is("c5.large"))
            .body("find { it.id == 'test' }.assets.find { it.id == 'test-cluster' }.'created-at'", notNullValue());
    }

    @Test
    void history_returnsCreateEntryForReconciledAsset()
    {
        given()
            .when().get("/environments/test/assets/test-llm/history")
            .then()
            .statusCode(200)
            .body("action", hasItem("CREATE"))
            .body("find { it.action == 'CREATE' }.snapshot.id", is("test-llm"))
            .body("find { it.action == 'CREATE' }.changed_at", notNullValue());
    }

    @Test
    void history_unknownEnvironment_returns404()
    {
        given()
            .when().get("/environments/does-not-exist/assets/whatever/history")
            .then()
            .statusCode(404);
    }
}
