package tech.illuin.wombat.feature;

import io.quarkiverse.flags.Flags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** A worker serves none of the features, only {@code /q/*}, whatever the flag configuration. */
@QuarkusTest
@TestProfile(WorkerFeaturesTest.WorkerProfile.class)
class WorkerFeaturesTest
{
    @Inject
    Flags flags;

    @Test
    void flagsAreAllDisabled()
    {
        Arrays.stream(Feature.values()).forEach(feature -> assertFalse(this.flags.isEnabled(feature.flag()), feature.flag()));
    }

    @Test
    void uiIsNotServed()
    {
        given().when().get("/api/app").then().statusCode(404);
        given().redirects().follow(false).when().get("/logout").then().statusCode(404);
    }

    @Test
    void readApiIsNotServed()
    {
        given().when().get("/api/environments").then().statusCode(404);
        given().when().get("/api/impact/environments").then().statusCode(404);
    }

    @Test
    void writeApiIsNotServed()
    {
        given().contentType(ContentType.JSON).body("[]").when().post("/api/metrics").then().statusCode(404);
    }

    @Test
    void administrationIsNotServed()
    {
        // Served to anonymous callers without authentication, when the feature is enabled
        given().when().get("/api/secrets").then().statusCode(404);
        given().when().post("/api/secrets/database/rewrap").then().statusCode(404);
    }

    @Test
    void healthIsServed()
    {
        given().when().get("/q/health/live").then().statusCode(200);
    }

    public static class WorkerProfile implements QuarkusTestProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            String kek = Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
            return Map.of(
                "wombat.mode", "worker",
                "quarkus.flags.runtime.ui.value", "true", // ignored: the mode alone sets the features
                "wombat.worker.id", "test-worker",
                "wombat.worker.aggregator.url", "http://localhost:0",
                "wombat.worker.aggregator.api-key", "unused",
                "wombat.secret.write-mode", "DATABASE",
                "wombat.secret.database.active-kek-id", "test",
                "wombat.secret.database.kek.test", kek
            );
        }
    }
}
