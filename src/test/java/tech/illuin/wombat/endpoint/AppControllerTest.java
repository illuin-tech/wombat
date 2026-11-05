package tech.illuin.wombat.endpoint;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class AppControllerTest
{
    @Test
    void get_returnsAnonymousUserAndUiSettingsWhenAuthenticationIsDisabled()
    {
        given()
            .when().get("/api/app")
            .then()
            .statusCode(200)
            .body("payload.auth.enabled", is(false))
            .body("payload.user.authenticated", is(false))
            .body("payload.user.permissions", hasItems("ui.dashboard.read", "api.user.read"))
            .body("payload.ui.maxDateRange.millis", greaterThan(0L))
            .body("payload.ui.maxDateRange.label", is("1 years"));
    }

    @Test
    void adminEndpointsReturnNotFoundWhenAuthenticationIsDisabled()
    {
        given()
            .when().get("/api/users")
            .then()
            .statusCode(404);

        given()
            .when().get("/api/keys/settings")
            .then()
            .statusCode(404);
    }
}
