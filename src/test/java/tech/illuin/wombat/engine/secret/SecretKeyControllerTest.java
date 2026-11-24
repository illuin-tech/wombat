package tech.illuin.wombat.engine.secret;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.security.PermissionAugmentor;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/** Runs with the secret write mode disabled: keys still resolve through the read-only resolvers. */
@QuarkusTest
@TestProfile(AuthenticationEnabledTest.AuthenticationEnabledProfile.class)
class SecretKeyControllerTest
{
    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "email", value = "boss@example.com"))
    void check_tellsWhetherKeyResolves()
    {
        given().queryParam("key", "test-kubeconfig")
            .when().get("/api/secret-keys/check")
            .then()
            .statusCode(200)
            .body("payload.key", is("test-kubeconfig"))
            .body("payload.exists", is(true));

        /* The environment resolver only reads prefixed variables: the process's own stay out of reach */
        given().queryParam("key", "PATH")
            .when().get("/api/secret-keys/check")
            .then()
            .statusCode(200)
            .body("payload.exists", is(false));

        given().queryParam("key", "WOMBAT_UNKNOWN_SECRET_KEY")
            .when().get("/api/secret-keys/check")
            .then()
            .statusCode(200)
            .body("payload.exists", is(false));
    }

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "email", value = "boss@example.com"))
    void check_rejectsInvalidKeys()
    {
        given().queryParam("key", "../etc/passwd").when().get("/api/secret-keys/check").then().statusCode(400);
        given().when().get("/api/secret-keys/check").then().statusCode(400);
    }

    @Test
    @TestSecurity(user = "user", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "email", value = "user@example.com"))
    void check_requiresSecretReadPermission()
    {
        given().queryParam("key", "PATH").when().get("/api/secret-keys/check").then().statusCode(403);
    }
}
