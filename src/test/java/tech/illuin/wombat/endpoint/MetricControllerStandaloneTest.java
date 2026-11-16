package tech.illuin.wombat.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

/** Runs in the default standalone mode, which accepts no pushed metrics. */
@QuarkusTest
class MetricControllerStandaloneTest
{
    @Test
    void push_isNotServedOutsideOfTheAggregatorMode()
    {
        given().contentType(ContentType.JSON).body("[]")
            .when().post("/api/workers/east/metrics")
            .then().statusCode(404);
    }

    @Test
    void declare_isNotServedOutsideOfTheAggregatorMode()
    {
        given().contentType(ContentType.JSON).body("{}")
            .when().put("/api/workers/east/environments")
            .then().statusCode(404);
    }

    @Test
    void openApi_listsNoWorkerEndpoint_butTheReadApi()
    {
        given().queryParam("format", "json")
            .when().get("/api/openapi")
            .then().statusCode(200)
            .body("paths", not(hasKey("/api/workers/{worker}/metrics")))
            .body("paths", not(hasKey("/api/workers/{worker}/environments")))
            .body("paths", hasKey("/api/environments"));
    }
}
