package tech.illuin.wombat.ui;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@QuarkusTest
@TestProfile(AuthenticationEnabledTest.AuthenticationEnabledProfile.class)
class AuthenticationEnabledTest
{
    @Test
    void restEndpoint_isRejectedWithoutIdentity()
    {
        given()
            .when().get("/environments")
            .then()
            .statusCode(401);
    }

    @Test
    void dashboard_isRejectedWithoutIdentity()
    {
        given()
            .when().get("/")
            .then()
            .statusCode(401);
    }

    @Test
    void publicPath_staysReachable()
    {
        given()
            .when().get("/q/metrics")
            .then()
            .statusCode(200);
    }

    @Test
    void logout_staysReachableWithoutIdentity()
    {
        given()
            .when().get("/logout")
            .then()
            .statusCode(200)
            .body(containsString("Signed out"));
    }

    @Test
    @TestSecurity(user = "someone")
    @OidcSecurity(claims = {
        @Claim(key = "email", value = "someone@example.com"),
    })
    void authenticatedCaller_reachesTheApi()
    {
        given()
            .when().get("/environments")
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "someone")
    @OidcSecurity(claims = {
        @Claim(key = "email", value = "someone@example.com"),
    })
    void dashboard_showsSignedInIndicatorAndLogoutLink()
    {
        given()
            .when().get("/")
            .then()
            .statusCode(200)
            .body(containsString("header-user"))
            .body(containsString("someone@example.com"))
            .body(containsString("href=\"/logout\""));
    }

    @Test
    void loggedOutPage_carriesNoIndicator()
    {
        given()
            .when().get("/logout")
            .then()
            .statusCode(200)
            .body(not(containsString("header-user")));
    }

    /** Applies the permissions of application-oidc.yaml, without the OIDC tenant which would need a reachable provider. */
    public static class AuthenticationEnabledProfile implements QuarkusTestProfile
    {
        @Override
        public String getConfigProfile()
        {
            return "test,oidc";
        }

        @Override
        public Map<String, String> getConfigOverrides()
        {
            return Map.of("quarkus.oidc.tenant-enabled", "false");
        }
    }
}
