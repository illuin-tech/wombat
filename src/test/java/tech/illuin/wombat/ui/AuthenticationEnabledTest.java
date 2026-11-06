package tech.illuin.wombat.ui;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.security.PermissionAugmentor;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@QuarkusTest
@TestProfile(AuthenticationEnabledTest.AuthenticationEnabledProfile.class)
public class AuthenticationEnabledTest
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

        given()
            .when().get("/impact/test")
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
    void logout_staysReachableWithoutIdentityAndRedirectsToTheSignedOutPage()
    {
        given().redirects().follow(false)
            .when().get("/logout")
            .then()
            .statusCode(303)
            .header("Location", endsWith("/signed-out?idp=false"));
    }

    @Test
    void postLogoutRedirect_landsOnTheSignedOutPage()
    {
        given().redirects().follow(false)
            .when().get("/logout/done")
            .then()
            .statusCode(303)
            .header("Location", endsWith("/signed-out?idp=true"));
    }

    @Test
    void appBootstrap_isPublicAndReturnsAnAnonymousUserWithoutIdentity()
    {
        given()
            .when().get("/api/app")
            .then()
            .statusCode(200)
            .body("payload.auth.enabled", is(true))
            .body("payload.user.authenticated", is(false))
            .body("payload.user.roles.size()", is(0))
            .body("payload.user.permissions.size()", is(0));
    }

    @Test
    @TestSecurity(user = "someone", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "email", value = "someone@example.com"),
    })
    void authenticatedCaller_reachesTheApi()
    {
        given()
            .when().get("/api/environments")
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "someone", roles = "unrelated", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "email", value = "someone@example.com"),
    })
    void callerWithoutMappedRole_isForbidden()
    {
        given()
            .when().get("/api/environments")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "someone", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "email", value = "someone@example.com"),
    })
    void user_listsTheirOwnKeysButNotEveryone()
    {
        given()
            .when().get("/api/keys")
            .then()
            .statusCode(200);

        given()
            .when().get("/api/keys?all=true")
            .then()
            .statusCode(403);
    }

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "email", value = "boss@example.com"),
    })
    void admin_managesApiKeys()
    {
        given()
            .when().get("/api/keys")
            .then()
            .statusCode(200);

        given()
            .when().get("/api/keys/settings")
            .then()
            .statusCode(200)
            .body("payload.grantablePermissions", containsInAnyOrder("api.impact.read", "api.simulation.read", "api.environment.read", "api.environment.write", "api.metric.write"))
            .body("payload.maxLifetimeDays", is(365))
            .body("payload.allowNeverExpiring", is(false));
    }

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "email", value = "boss@example.com"),
    })
    void admin_opensTheUsersPage()
    {
        given()
            .when().get("/users")
            .then()
            .statusCode(not(anyOf(is(401), is(403))));

        given()
            .when().get("/api/app")
            .then()
            .statusCode(200)
            .body("payload.user.permissions", hasItems("ui.users.read", "ui.apikeys.manage"));

        given()
            .when().get("/api/users")
            .then()
            .statusCode(200)
            .body("payload.find { it.email == 'boss@example.com' }.self", is(true))
            .body("payload.find { it.email == 'boss@example.com' }.roles.find { it.role == 'admin' }.fromProvider", is(true));
    }

    @Test
    @TestSecurity(user = "someone", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "email", value = "someone@example.com"),
    })
    void user_cannotOpenTheUsersPage()
    {
        given()
            .when().get("/users")
            .then()
            .statusCode(403);

        given()
            .when().get("/api/users")
            .then()
            .statusCode(403);

        given()
            .when().get("/")
            .then()
            .statusCode(not(anyOf(is(401), is(403))));

        given()
            .when().get("/api/app")
            .then()
            .statusCode(200)
            .body("payload.user.permissions", not(hasItems("ui.users.read")));
    }

    @Test
    @TestSecurity(user = "someone", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "email", value = "someone@example.com"),
    })
    void appBootstrap_reportsAuthenticatedUserAndPermissions()
    {
        given()
            .when().get("/api/app")
            .then()
            .statusCode(200)
            .body("payload.user.authenticated", is(true))
            .body("payload.user.name", is("someone@example.com"))
            .body("payload.user.roles", hasItems("user"))
            .body("payload.user.permissions", hasItems("ui.dashboard.read", "api.impact.read"));
    }

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "email", value = "boss@example.com"),
    })
    void admin_opensTheApiKeysPage()
    {
        given()
            .when().get("/api-keys")
            .then()
            .statusCode(not(anyOf(is(401), is(403))));

        given()
            .when().get("/api/keys?all=true")
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "someone", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "email", value = "someone@example.com"),
    })
    void user_opensTheApiKeysPageForTheirOwnKeys()
    {
        given()
            .when().get("/api-keys")
            .then()
            .statusCode(not(anyOf(is(401), is(403))));

        given()
            .when().get("/api/app")
            .then()
            .statusCode(200)
            .body("payload.user.permissions", hasItems("ui.apikeys.manage", "api.apikey.manage"))
            .body("payload.user.permissions", not(hasItems("api.apikey.admin")));

        given()
            .when().get("/api/keys/settings")
            .then()
            .statusCode(200)
            .body("payload.grantablePermissions", hasItems("api.impact.read"));
    }

    @Test
    @TestSecurity(user = "keeper", roles = "wombat-key-managers", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "email", value = "keeper@example.com"),
    })
    void apiKeyManager_isAUserWhoAlsoSeesEveryKey()
    {
        given()
            .when().get("/api-keys")
            .then()
            .statusCode(not(anyOf(is(401), is(403))));

        given()
            .when().get("/api/app")
            .then()
            .statusCode(200)
            .body("payload.user.permissions", hasItems("ui.apikeys.manage", "api.apikey.admin"))
            .body("payload.user.permissions", not(hasItems("ui.users.read")));

        given().when().get("/api/keys?all=true").then().statusCode(200);
        given().when().get("/").then().statusCode(not(anyOf(is(401), is(403))));
        given().when().get("/users").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "chief", roles = "unrelated", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "email", value = "Chief@Example.com"),
        @Claim(key = "email_verified", value = "true"),
    })
    void verifiedEmailListedForARole_getsThatRole()
    {
        given()
            .when().get("/users")
            .then()
            .statusCode(not(anyOf(is(401), is(403))));
    }

    @Test
    @TestSecurity(user = "chief", roles = "unrelated", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "email", value = "chief@example.com"),
        @Claim(key = "email_verified", value = "false"),
    })
    void unverifiedEmailListedForARole_getsNothing()
    {
        given()
            .when().get("/api/environments")
            .then()
            .statusCode(403);
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
            return Map.of(
                "quarkus.oidc.tenant-enabled", "false",
                "auth.roles.admin", "wombat-admins,chief@example.com",
                "auth.roles.user", "wombat-users",
                "auth.roles.api-key-manager", "wombat-key-managers"
            );
        }
    }
}
