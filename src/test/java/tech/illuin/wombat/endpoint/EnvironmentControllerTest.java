package tech.illuin.wombat.endpoint;

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
            .when().get("/api/environments")
            .then()
            .statusCode(200)
            .body("payload.id", hasItem("test"))
            .body("payload.find { it.id == 'test' }.name", is("test"))
            .body("payload.find { it.id == 'test' }.uuid", notNullValue())
            .body("payload.find { it.id == 'test' }.'created-at'", notNullValue())
            .body("payload.find { it.id == 'test' }.'disabled-at'", nullValue())
            .body("payload.find { it.id == 'test' }.assets.id", hasItem("test-cluster"))
            .body("payload.find { it.id == 'test' }.assets.id", hasItem("test-llm"))
            .body("payload.find { it.id == 'test' }.assets.find { it.id == 'test-cluster' }.namespace", is("test-ns"))
            .body("payload.find { it.id == 'test' }.assets.find { it.id == 'test-cluster' }.profile.'instance-type'", is("c5.large"))
            .body("payload.find { it.id == 'test' }.assets.find { it.id == 'test-cluster' }.'created-at'", notNullValue());
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
            .body("payload.find { it.action == 'CREATE' }.changed_at", notNullValue());
    }

    @Test
    void history_unknownEnvironment_returns404()
    {
        given()
            .when().get("/api/environments/does-not-exist/assets/whatever/history")
            .then()
            .statusCode(404);
    }
}
