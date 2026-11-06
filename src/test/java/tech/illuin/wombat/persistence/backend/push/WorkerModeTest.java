package tech.illuin.wombat.persistence.backend.push;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.persistence.backend.PersistenceEngine;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(WorkerModeTest.WorkerProfile.class)
class WorkerModeTest
{
    @Inject
    PersistenceEngine engine;

    /* The datasource's backend stays mounted for the configuration tables, but metrics go to the push one. */
    @Test
    void worker_mountsThePushBackendNextToItsDatabase()
    {
        assertTrue(this.engine.isMounted(PushBackendConfig.BACKEND_PUSH));
        assertTrue(this.engine.isMounted("sqlite"));
        assertInstanceOf(PushMetricStore.class, this.engine.stores(PushBackendConfig.BACKEND_PUSH).kubernetes());
    }

    @Test
    void worker_acceptsNoPushedMetrics()
    {
        given().contentType(ContentType.JSON).body("[]").when().post("/api/metrics").then().statusCode(404);
    }

    public static class WorkerProfile implements QuarkusTestProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            return Map.of(
                "wombat.mode", "worker",
                "wombat.worker.id", "test-worker",
                "wombat.worker.aggregator.url", "http://localhost:0",
                "wombat.worker.aggregator.api-key", "unused"
            );
        }
    }
}
