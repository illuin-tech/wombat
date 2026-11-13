package tech.illuin.wombat.security.apikey;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.Role;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestProfile(MetricPushAuthorizationTest.AuthenticatedAggregatorProfile.class)
class MetricPushAuthorizationTest
{
    @Inject
    ApiKeyService service;

    @Test
    void key_pushesMetricsOnlyWithTheWritePermission()
    {
        String writer = this.issue(Permission.Names.API_METRIC_WRITE);
        String reader = this.issue(Permission.Names.API_ENVIRONMENT_READ);

        given().header(ApiKeyAuthenticationMechanism.HEADER, writer).contentType(ContentType.JSON).body("[]").when().post("/api/workers/east/metrics").then().statusCode(200);
        given().header(ApiKeyAuthenticationMechanism.HEADER, reader).contentType(ContentType.JSON).body("[]").when().post("/api/workers/east/metrics").then().statusCode(403);
        given().contentType(ContentType.JSON).body("[]").when().post("/api/workers/east/metrics").then().statusCode(401);
    }

    private String issue(String permission)
    {
        Caller admin = new Caller("https://idp", UUID.randomUUID().toString(), "admin", "admin@example.com", EnumSet.of(Role.ADMIN), Role.ADMIN.permissions());
        return this.service.create(admin, "worker", List.of(permission), Instant.now().plus(Duration.ofDays(1))).token();
    }

    public static class AuthenticatedAggregatorProfile extends AuthenticationEnabledTest.AuthenticationEnabledProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            Map<String, String> overrides = new HashMap<>(super.getConfigOverrides());
            overrides.put("wombat.mode", "aggregator");
            return overrides;
        }
    }
}
