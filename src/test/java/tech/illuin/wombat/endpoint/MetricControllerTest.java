package tech.illuin.wombat.endpoint;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.impact.llm.LLMModelMetricRepository;
import tech.illuin.wombat.persistence.model.LLMMetricEntity;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(AggregatorProfile.class)
class MetricControllerTest
{
    /* The start of the worker's window, nowhere near the aggregator's clock. */
    private static final long WORKER_INSTANT_MS = 1_700_000_100_000L;

    @Inject
    LLMModelMetricRepository llm;

    @BeforeEach
    void setUp()
    {
        QuarkusTransaction.requiringNew().run(() -> this.llm.delete("assetId in ?1", List.of("east.pushed-llm", "east.a")));
    }

    @Test
    void push_savesEverySample_atTheStartOfTheWorkersWindow()
    {
        given().contentType(ContentType.JSON).body("""
            [
              {"asset": {"id": "pushed-llm", "environmentId": "remote", "name": "Pushed LLM", "type": "tech.illuin.wombat-module.llm-static"}, "window": {"start": "2023-11-14T22:15:00Z", "end": "2023-11-14T22:20:00Z"}, "data": {"family": "LLM", "serviceId": "chat", "provider": "mistralai", "model": "mistral-large-latest", "location": "FRA", "outputTokens": 10, "requests": 1}},
              {"asset": {"id": "pushed-llm", "environmentId": "remote", "name": "Pushed LLM", "type": "tech.illuin.wombat-module.llm-static"}, "window": {"start": "2023-11-14T22:15:00Z", "end": "2023-11-14T22:20:00Z"}, "data": {"family": "LLM", "serviceId": "chat", "provider": "mistralai", "model": "mistral-large-latest", "location": "FRA", "outputTokens": 20, "requests": 1}}
            ]
            """)
            .when().post("/api/workers/east/metrics")
            .then().statusCode(200)
            .body("payload", is(2))
            .body("status.message", is("Success"))
            .body("status.metadata.errors", nullValue());

        List<LLMMetricEntity> rows = this.llm.list("assetId", "east.pushed-llm");
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(row -> row.instantMs == WORKER_INSTANT_MS));
        assertEquals(30L, rows.stream().mapToLong(row -> row.outputTokens).sum());
    }

    @Test
    void push_skipsAnUnknownAssetType_reportsItInTheStatus_andSavesTheOthers()
    {
        given().contentType(ContentType.JSON).body("""
            [
              {"asset": {"id": "a", "environmentId": "e", "name": "A", "type": "unknown.type"}, "window": {"start": "2023-11-14T22:15:00Z", "end": "2023-11-14T22:20:00Z"}, "data": {"family": "LLM", "serviceId": "chat", "provider": "mistralai", "model": "mistral-large-latest", "location": "FRA", "outputTokens": 10, "requests": 1}},
              {"asset": {"id": "pushed-llm", "environmentId": "remote", "name": "Pushed LLM", "type": "tech.illuin.wombat-module.llm-static"}, "window": {"start": "2023-11-14T22:15:00Z", "end": "2023-11-14T22:20:00Z"}, "data": {"family": "LLM", "serviceId": "chat", "provider": "mistralai", "model": "mistral-large-latest", "location": "FRA", "outputTokens": 10, "requests": 1}}
            ]
            """)
            .when().post("/api/workers/east/metrics")
            .then().statusCode(200)
            .body("payload", is(1))
            .body("status.message", is("Saved 1 metric(s), skipped 1"))
            .body("status.metadata.errors", hasSize(1))
            .body("status.metadata.errors[0].assetId", is("a"))
            .body("status.metadata.errors[0].assetType", is("unknown.type"))
            .body("status.metadata.errors[0].reason", is("No active module for asset type unknown.type"))
            .body("status.metadata.errors[0].count", is(1));

        assertEquals(0, this.llm.count("assetId", "east.a"));
        assertEquals(1, this.llm.count("assetId", "east.pushed-llm"));
    }

    @Test
    void push_rejectsAMissingWindow()
    {
        given().contentType(ContentType.JSON).body("""
            [{"asset": {"id": "pushed-llm", "environmentId": "remote", "name": "Pushed LLM", "type": "tech.illuin.wombat-module.llm-static"}, "data": {"family": "LLM", "serviceId": "chat", "provider": "mistralai", "model": "mistral-large-latest", "location": "FRA", "outputTokens": 10, "requests": 1}}]
            """)
            .when().post("/api/workers/east/metrics")
            .then().statusCode(400);
    }

    @Test
    void push_rejectsAWindowEndingBeforeItStarts()
    {
        given().contentType(ContentType.JSON).body("""
            [{"asset": {"id": "pushed-llm", "environmentId": "remote", "name": "Pushed LLM", "type": "tech.illuin.wombat-module.llm-static"}, "window": {"start": "2023-11-14T22:20:00Z", "end": "2023-11-14T22:15:00Z"}, "data": {"family": "LLM", "serviceId": "chat", "provider": "mistralai", "model": "mistral-large-latest", "location": "FRA", "outputTokens": 10, "requests": 1}}]
            """)
            .when().post("/api/workers/east/metrics")
            .then().statusCode(400);
    }

    @Test
    void push_rejectsAnUnknownFamily()
    {
        given().contentType(ContentType.JSON).body("""
            [{"asset": {"id": "pushed-llm", "environmentId": "remote", "name": "Pushed LLM", "type": "tech.illuin.wombat-module.llm-static"}, "window": {"start": "2023-11-14T22:15:00Z", "end": "2023-11-14T22:20:00Z"}, "data": {"family": "NOPE"}}]
            """)
            .when().post("/api/workers/east/metrics")
            .then().statusCode(400);
    }

    @Test
    void push_rejectsAMissingAsset()
    {
        given().contentType(ContentType.JSON).body("""
            [{"window": {"start": "2023-11-14T22:15:00Z", "end": "2023-11-14T22:20:00Z"}, "data": {"family": "LLM", "serviceId": "chat", "provider": "mistralai", "model": "mistral-large-latest", "location": "FRA", "outputTokens": 10, "requests": 1}}]
            """)
            .when().post("/api/workers/east/metrics")
            .then().statusCode(400);
    }
}
