package tech.illuin.wombat.endpoint.access;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentRepository;
import tech.illuin.wombat.security.PermissionAugmentor;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Users read environments and assets, admins also manage them.
 */
@QuarkusTest
@TestProfile(AuthenticationEnabledTest.AuthenticationEnabledProfile.class)
class ContextAccessControlTest
{
    private static final String CREATED = "access-control-env";

    @Inject EnvironmentRepository environments;

    @AfterEach
    void clean()
    {
        QuarkusTransaction.requiringNew().run(() -> this.environments.findByIdOptional(CREATED).ifPresent(this.environments::delete));
    }

    @Test
    void anonymous_isRejected()
    {
        given().when().get("/api/environments").then().statusCode(401);
        given().when().get("/api/assets").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "reader", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    void users_read_butDoNotManage()
    {
        given().when().get("/api/environments/test").then().statusCode(200);
        given().when().get("/api/assets/test-llm").then().statusCode(200);

        given().contentType(ContentType.JSON).body(Map.of("id", CREATED, "name", "Nope")).when().post("/api/environments").then().statusCode(403);
        given().contentType(ContentType.JSON).body(Map.of("name", "Nope")).when().put("/api/environments/test").then().statusCode(403);
        given().when().delete("/api/environments/test").then().statusCode(403);
        given().contentType(ContentType.JSON).body(Map.of("id", "nope", "environmentId", "test", "name", "Nope", "type", "x", "properties", Map.of()))
            .when().post("/api/assets").then().statusCode(403);
        given().contentType(ContentType.JSON).body(Map.of("name", "Nope", "properties", Map.of())).when().put("/api/assets/test-llm").then().statusCode(403);
        given().when().delete("/api/assets/test-llm").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    void admins_manage()
    {
        given().contentType(ContentType.JSON).body(Map.of("id", CREATED, "name", "Created")).when().post("/api/environments").then().statusCode(201);
        given().when().delete("/api/environments/" + CREATED).then().statusCode(204);
        // Past the permission check: provisioned assets are read-only.
        given().when().delete("/api/assets/test-llm").then().statusCode(409);
    }
}
