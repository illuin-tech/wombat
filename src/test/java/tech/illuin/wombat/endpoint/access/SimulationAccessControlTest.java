package tech.illuin.wombat.endpoint.access;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.simulation.persistence.SimulationAssetRepository;
import tech.illuin.wombat.engine.simulation.persistence.SimulationRepository;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.PermissionAugmentor;
import tech.illuin.wombat.security.Role;
import tech.illuin.wombat.security.apikey.ApiKeyAuthenticationMechanism;
import tech.illuin.wombat.security.apikey.ApiKeyService;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * Users read simulations, admins also manage them. Copying an asset into one also takes reading assets, which an API
 * key may lack.
 */
@QuarkusTest
@TestProfile(AuthenticationEnabledTest.AuthenticationEnabledProfile.class)
class SimulationAccessControlTest
{
    private static final String CREATED = "access-control-sim";

    @Inject SimulationRepository simulations;
    @Inject SimulationAssetRepository simulationAssets;
    @Inject ApiKeyService apiKeys;

    @AfterEach
    void clean()
    {
        QuarkusTransaction.requiringNew().run(() -> {
            this.simulationAssets.deleteBySimulation(CREATED);
            this.simulations.findByIdOptional(CREATED).ifPresent(this.simulations::delete);
        });
    }

    @Test
    void anonymous_isRejected()
    {
        given().when().get("/api/simulations").then().statusCode(401);
        given().when().get("/api/simulations/" + CREATED + "/assets").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "reader", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    void users_read_butDoNotManage()
    {
        given().when().get("/api/simulations").then().statusCode(200);
        given().when().get("/api/simulations/" + CREATED).then().statusCode(404);

        given().contentType(ContentType.JSON).body(Map.of("id", CREATED, "name", "Nope")).when().post("/api/simulations").then().statusCode(403);
        given().contentType(ContentType.JSON).body(Map.of("name", "Nope", "referenceWindow", "DAY")).when().put("/api/simulations/" + CREATED).then().statusCode(403);
        given().contentType(ContentType.JSON).body(Map.of("id", "nope", "name", "Nope")).when().post("/api/simulations/" + CREATED + "/duplicate").then().statusCode(403);
        given().when().delete("/api/simulations/" + CREATED).then().statusCode(403);
        given().contentType(ContentType.JSON).body(Map.of("name", "Nope", "parentAssetId", "test-llm"))
            .when().post("/api/simulations/" + CREATED + "/assets").then().statusCode(403);
        given().contentType(ContentType.JSON).body(Map.of("name", "Nope", "properties", Map.of()))
            .when().put("/api/simulations/" + CREATED + "/assets/test-llm").then().statusCode(403);
        given().when().delete("/api/simulations/" + CREATED + "/assets/test-llm").then().statusCode(403);
        given().when().post("/api/simulations/" + CREATED + "/assets/test-llm/sync").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    void admins_manage()
    {
        given().contentType(ContentType.JSON).body(Map.of("id", CREATED, "name", "Created", "parentEnvironmentId", "test", "assetIds", List.of("test-llm")))
            .when().post("/api/simulations").then().statusCode(201);
        given().when().post("/api/simulations/" + CREATED + "/assets/test-llm/sync").then().statusCode(200);
        given().when().delete("/api/simulations/" + CREATED).then().statusCode(204);
    }

    @Test
    void aKeyWithoutAssetRead_cannotCopyAssets()
    {
        String key = this.issue(Permission.API_SIMULATION_READ, Permission.API_SIMULATION_WRITE);

        given().header(ApiKeyAuthenticationMechanism.HEADER, key).contentType(ContentType.JSON)
            .body(Map.of("id", CREATED, "name", "Created", "parentEnvironmentId", "test", "assetIds", List.of("test-llm")))
            .when().post("/api/simulations").then().statusCode(403);
        given().header(ApiKeyAuthenticationMechanism.HEADER, key).contentType(ContentType.JSON)
            .body(Map.of("id", CREATED, "name", "Created"))
            .when().post("/api/simulations").then().statusCode(201);
        given().header(ApiKeyAuthenticationMechanism.HEADER, key).contentType(ContentType.JSON)
            .body(Map.of("name", "Copy", "parentAssetId", "test-llm"))
            .when().post("/api/simulations/" + CREATED + "/assets").then().statusCode(403);
    }

    private String issue(Permission... permissions)
    {
        Caller admin = new Caller("https://idp", UUID.randomUUID().toString(), "admin", "admin@example.com", EnumSet.of(Role.ADMIN), Role.ADMIN.permissions());
        List<String> values = Arrays.stream(permissions).map(Permission::value).toList();
        return this.apiKeys.create(admin, "simulations", values, Instant.now().plus(Duration.ofDays(1))).token();
    }
}
