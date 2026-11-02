package tech.illuin.wombat.security.apikey;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.PermissionAugmentor;
import tech.illuin.wombat.security.Role;
import tech.illuin.wombat.security.user.UserRepository;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@TestProfile(AuthenticationEnabledTest.AuthenticationEnabledProfile.class)
class ApiKeyAuthenticationTest
{
    @Inject
    ApiKeyService service;

    @Inject
    ApiKeyRepository keys;

    @Inject
    UserRepository users;

    @Test
    void key_reachesTheApiWithinItsPermissions()
    {
        String token = this.issue(admin(), List.of(Permission.Names.API_ENVIRONMENT_READ)).token();

        given().auth().oauth2(token).when().get("/api/environments").then().statusCode(200);
        given().auth().oauth2(token).contentType(ContentType.JSON).body("{}").when().post("/api/simulation").then().statusCode(403);
    }

    @Test
    void key_neverReachesTheUiNorKeyManagement()
    {
        String token = this.issue(admin(), List.of(Permission.Names.API_ENVIRONMENT_READ)).token();

        given().auth().oauth2(token).redirects().follow(false).when().get("/").then().statusCode(403);
        given().auth().oauth2(token).when().get("/api/keys").then().statusCode(403);
        given().auth().oauth2(token).when().get("/users").then().statusCode(403);
    }

    @Test
    void key_doesNotDependOnItsCreator()
    {
        Caller creator = admin();
        String token = this.issue(creator, List.of(Permission.Names.API_ENVIRONMENT_READ)).token();
        QuarkusTransaction.requiringNew().run(() -> this.users.findByIdentity(creator.issuer(), creator.subject()).orElseThrow().roles = EnumSet.noneOf(Role.class));

        given().auth().oauth2(token).when().get("/api/environments").then().statusCode(200);
    }

    @Test
    void revokedKey_isRejected()
    {
        ApiKeyService.Issued issued = this.issue(admin(), List.of(Permission.Names.API_ENVIRONMENT_READ));
        this.service.revoke(issued.key().id);

        given().auth().oauth2(issued.token()).when().get("/api/environments").then().statusCode(401);
    }

    @Test
    void expiredKey_isRejected()
    {
        ApiKeyService.Issued issued = this.issue(admin(), List.of(Permission.Names.API_ENVIRONMENT_READ));
        QuarkusTransaction.requiringNew().run(() -> this.keys.findById(issued.key().id).expiresAt = Instant.now().minusSeconds(1));

        given().auth().oauth2(issued.token()).when().get("/api/environments").then().statusCode(401);
    }

    @Test
    void lastUse_isRecorded()
    {
        ApiKeyService.Issued issued = this.issue(admin(), List.of(Permission.Names.API_ENVIRONMENT_READ));

        given().auth().oauth2(issued.token()).when().get("/api/environments").then().statusCode(200);
        QuarkusTransaction.requiringNew().run(() -> assertNotNull(this.keys.findById(issued.key().id).lastUsedAt));
    }

    @Test
    void keys_onlyCarryPermissionsTheCreatorHolds()
    {
        Caller caller = new Caller("https://idp", UUID.randomUUID().toString(), "limited", null, EnumSet.noneOf(Role.class), EnumSet.of(Permission.API_IMPACT_READ));

        assertThrows(ForbiddenException.class, () -> this.issue(caller, List.of(Permission.Names.API_ENVIRONMENT_READ)));
    }

    @Test
    void keys_neverCarryManagementOrUiPermissions()
    {
        assertThrows(BadRequestException.class, () -> this.issue(admin(), List.of(Permission.Names.API_APIKEY_MANAGE)));
        assertThrows(BadRequestException.class, () -> this.issue(admin(), List.of(Permission.Names.UI_DASHBOARD_READ)));
    }

    @Test
    void keys_cannotOutliveTheMaximumLifetime()
    {
        assertThrows(BadRequestException.class, () -> this.service.create(admin(), "too-long", List.of(Permission.Names.API_ENVIRONMENT_READ), Instant.now().plus(Duration.ofDays(365))));
        assertThrows(BadRequestException.class, () -> this.service.create(admin(), "past", List.of(Permission.Names.API_ENVIRONMENT_READ), Instant.now().minusSeconds(1)));
    }

    @Test
    void neverExpiringKeys_areDisabledByDefault()
    {
        assertThrows(BadRequestException.class, () -> this.service.create(admin(), "forever", List.of(Permission.Names.API_ENVIRONMENT_READ), null));
    }

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "iss", value = "https://idp"),
        @Claim(key = "sub", value = "boss"),
        @Claim(key = "email", value = "boss@example.com"),
    })
    void admin_createsListsAndRevokesKeys()
    {
        String id = given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "ci", "permissions", List.of(Permission.Names.API_ENVIRONMENT_READ), "expires-at", Instant.now().plus(Duration.ofDays(1)).toString()))
            .when().post("/api/keys")
            .then()
            .statusCode(200)
            .body("payload.token", notNullValue())
            .body("payload.created-by", is("boss@example.com"))
            .extract().path("payload.id");

        ApiKeyService.Issued other = this.issue(admin(), List.of(Permission.Names.API_IMPACT_READ));

        given()
            .when().get("/api/keys")
            .then()
            .statusCode(200)
            .body("payload.find { it.id == '" + id + "' }.created-by", is("boss@example.com"))
            .body("payload.find { it.id == '" + other.key().id + "' }.name", is("test"));

        given().when().delete("/api/keys/" + other.key().id).then().statusCode(200);
        given().auth().oauth2(other.token()).when().get("/api/impact/environments").then().statusCode(401);
    }

    private ApiKeyService.Issued issue(Caller caller, List<String> permissions)
    {
        return this.service.create(caller, "test", permissions, Instant.now().plus(Duration.ofDays(1)));
    }

    private static Caller admin()
    {
        return new Caller("https://idp", UUID.randomUUID().toString(), "admin", "admin@example.com", EnumSet.of(Role.ADMIN), Role.ADMIN.permissions());
    }
}
