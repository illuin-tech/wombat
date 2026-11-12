package tech.illuin.wombat.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/** Runs in the default standalone mode, which accepts no pushed metrics. */
@QuarkusTest
class MetricControllerStandaloneTest
{
    @Test
    void push_isNotServedOutsideOfTheAggregatorMode()
    {
        given().contentType(ContentType.JSON).body("[]")
            .when().post("/api/metrics")
            .then().statusCode(404);
    }

    @Test
    void declare_isNotServedOutsideOfTheAggregatorMode()
    {
        given().contentType(ContentType.JSON).body("{}")
            .when().put("/api/workers/east/environments")
            .then().statusCode(404);
    }
}
