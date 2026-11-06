package tech.illuin.wombat.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@TestProfile(AggregatorProfile.class)
class MetricControllerTest
{
    @Test
    void push_stagesEveryMetric()
    {
        given().contentType(ContentType.JSON).body("""
            [{
              "asset": {"id": "pushed-llm", "environmentId": "remote", "name": "Pushed LLM"},
              "assetType": "tech.illuin.wombat-module.llm-static",
              "metrics": [
                {"family": "LLM", "serviceId": "chat", "provider": "mistralai", "model": "mistral-large-latest", "location": "FRA", "outputTokens": 10, "requests": 1},
                {"family": "LLM", "serviceId": "chat", "provider": "mistralai", "model": "mistral-large-latest", "location": "FRA", "outputTokens": 20, "requests": 1}
              ]
            }]
            """)
            .when().post("/api/metrics")
            .then().statusCode(200).body("payload", is(2));
    }

    @Test
    void push_rejectsAnUnknownAssetType()
    {
        given().contentType(ContentType.JSON).body("""
            [{"asset": {"id": "a", "environmentId": "e", "name": "A"}, "assetType": "unknown.type", "metrics": []}]
            """)
            .when().post("/api/metrics")
            .then().statusCode(400);
    }

    @Test
    void push_rejectsAnUnknownFamily()
    {
        given().contentType(ContentType.JSON).body("""
            [{"asset": {"id": "a", "environmentId": "e", "name": "A"}, "assetType": "tech.illuin.wombat-module.llm-static", "metrics": [{"family": "NOPE"}]}]
            """)
            .when().post("/api/metrics")
            .then().statusCode(400);
    }

    @Test
    void push_rejectsAMissingAsset()
    {
        given().contentType(ContentType.JSON).body("""
            [{"assetType": "tech.illuin.wombat-module.llm-static", "metrics": []}]
            """)
            .when().post("/api/metrics")
            .then().statusCode(400);
    }
}
