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
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

        given().header(ApiKeyAuthenticationMechanism.HEADER, token).when().get("/api/environments").then().statusCode(200);
        given().header(ApiKeyAuthenticationMechanism.HEADER, token).contentType(ContentType.JSON).body("{}").when().post("/api/simulation").then().statusCode(403);
    }

    @Test
    void key_neverReachesTheUiNorKeyManagement()
    {
        String token = this.issue(admin(), List.of(Permission.Names.API_ENVIRONMENT_READ)).token();

        given().header(ApiKeyAuthenticationMechanism.HEADER, token).redirects().follow(false).when().get("/").then().statusCode(403);
        given().header(ApiKeyAuthenticationMechanism.HEADER, token).when().get("/api/keys").then().statusCode(403);
        given().header(ApiKeyAuthenticationMechanism.HEADER, token).when().get("/users").then().statusCode(403);
    }

    @Test
    void key_stopsWorkingWhenItsOwnerLosesTheirRoles()
    {
        Caller owner = admin();
        String token = this.issue(owner, List.of(Permission.Names.API_ENVIRONMENT_READ)).token();
        given().header(ApiKeyAuthenticationMechanism.HEADER, token).when().get("/api/environments").then().statusCode(200);

        QuarkusTransaction.requiringNew().run(() -> this.users.findByIdentity(owner.issuer(), owner.subject()).orElseThrow().roles = EnumSet.noneOf(Role.class));

        given().header(ApiKeyAuthenticationMechanism.HEADER, token).when().get("/api/environments").then().statusCode(401);
    }


    @Test
    void revokedKey_isRejected()
    {
        ApiKeyService.Issued issued = this.issue(admin(), List.of(Permission.Names.API_ENVIRONMENT_READ));
        this.service.revoke(admin(), issued.key().id);

        given().header(ApiKeyAuthenticationMechanism.HEADER, issued.token()).when().get("/api/environments").then().statusCode(401);
    }

    @Test
    void expiredKey_isRejected()
    {
        ApiKeyService.Issued issued = this.issue(admin(), List.of(Permission.Names.API_ENVIRONMENT_READ));
        QuarkusTransaction.requiringNew().run(() -> this.keys.findById(issued.key().id).expiresAt = Instant.now().minusSeconds(1));

        given().header(ApiKeyAuthenticationMechanism.HEADER, issued.token()).when().get("/api/environments").then().statusCode(401);
    }

    @Test
    void unknownKey_isRejected()
    {
        given().header(ApiKeyAuthenticationMechanism.HEADER, ApiKeyTokens.generate()).when().get("/api/environments").then().statusCode(401);
    }

    @Test
    void key_isOnlyAcceptedInItsOwnHeader()
    {
        String token = this.issue(admin(), List.of(Permission.Names.API_ENVIRONMENT_READ)).token();

        given().auth().oauth2(token).when().get("/api/environments").then().statusCode(401);
    }

    @Test
    void onlyTheKeyHash_isStored()
    {
        ApiKeyService.Issued issued = this.issue(admin(), List.of(Permission.Names.API_ENVIRONMENT_READ));

        QuarkusTransaction.requiringNew().run(() -> assertEquals(ApiKeyTokens.hash(issued.token()), this.keys.findById(issued.key().id).keyHash));
    }

    @Test
    void lastUse_isRecorded()
    {
        ApiKeyService.Issued issued = this.issue(admin(), List.of(Permission.Names.API_ENVIRONMENT_READ));

        given().header(ApiKeyAuthenticationMechanism.HEADER, issued.token()).when().get("/api/environments").then().statusCode(200);
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
        assertThrows(BadRequestException.class, () -> this.issue(admin(), List.of(Permission.Names.API_SECRET_MANAGE)));
    }

    @Test
    void keys_cannotOutliveTheMaximumLifetime()
    {
        assertThrows(BadRequestException.class, () -> this.service.create(admin(), "too-long", List.of(Permission.Names.API_ENVIRONMENT_READ), Instant.now().plus(Duration.ofDays(366))));
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
        Instant expiresAt = Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MILLIS);
        Map<String, Object> createdKey = given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "ci", "permissions", List.of(Permission.Names.API_ENVIRONMENT_READ), "expiresAt", expiresAt.toString()))
            .when().post("/api/keys")
            .then()
            .statusCode(200)
            .extract().path("payload");
        String id = (String) createdKey.get("id");
        assertEquals("boss@example.com", createdKey.get("owner"));
        assertNotNull(createdKey.get("createdAt"));
        assertEquals(expiresAt.toString(), createdKey.get("expiresAt"));
        assertNull(createdKey.get("revokedAt"));
        assertNull(createdKey.get("lastUsedAt"));
        assertNotNull(createdKey.get("token"));
        assertFalse(createdKey.containsKey("created-at"));
        assertFalse(createdKey.containsKey("expires-at"));
        assertFalse(createdKey.containsKey("revoked-at"));
        assertFalse(createdKey.containsKey("last-used-at"));

        ApiKeyService.Issued other = this.issue(admin(), List.of(Permission.Names.API_IMPACT_READ));

        Map<String, Object> listedKey = given()
            .when().get("/api/keys")
            .then()
            .statusCode(200)
            .body("payload.find { it.id == '" + id + "' }.owner", is("boss@example.com"))
            .body("payload.find { it.id == '" + other.key().id + "' }", nullValue())
            .extract().path("payload.find { it.id == '" + id + "' }");

        assertNotNull(listedKey.get("createdAt"));
        assertEquals(expiresAt.toString(), listedKey.get("expiresAt"));
        assertNull(listedKey.get("revokedAt"));
        assertNull(listedKey.get("lastUsedAt"));
        assertFalse(listedKey.containsKey("token"));
        assertFalse(listedKey.containsKey("created-at"));
        assertFalse(listedKey.containsKey("expires-at"));
        assertFalse(listedKey.containsKey("revoked-at"));
        assertFalse(listedKey.containsKey("last-used-at"));

        given()
            .when().get("/api/keys?all=true")
            .then()
            .statusCode(200)
            .body("payload.find { it.id == '" + other.key().id + "' }.owner", is("admin"));

        given().when().delete("/api/keys/" + other.key().id).then().statusCode(200);
        given().header(ApiKeyAuthenticationMechanism.HEADER, other.token()).when().get("/api/impact/environments").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "alice", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "iss", value = "https://idp"),
        @Claim(key = "sub", value = "alice"),
        @Claim(key = "email", value = "alice@example.com"),
    })
    void user_managesOnlyTheirOwnKeys()
    {
        String id = given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "script", "permissions", List.of(Permission.Names.API_IMPACT_READ), "expiresAt", Instant.now().plus(Duration.ofDays(1)).toString()))
            .when().post("/api/keys")
            .then()
            .statusCode(200)
            .body("payload.owner", is("alice@example.com"))
            .extract().path("payload.id");
        ApiKeyService.Issued other = this.issue(admin(), List.of(Permission.Names.API_IMPACT_READ));

        given().when().get("/api/keys").then()
            .statusCode(200)
            .body("payload.id", hasItem(id))
            .body("payload.id", not(hasItem(other.key().id)));
        given().when().get("/api/keys?all=true").then().statusCode(403);

        given().when().delete("/api/keys/" + other.key().id).then().statusCode(404);
        given().header(ApiKeyAuthenticationMechanism.HEADER, other.token()).when().get("/api/impact/environments").then().statusCode(200);

        given().when().delete("/api/keys/" + id).then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "alice", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "iss", value = "https://idp"),
        @Claim(key = "sub", value = "alice"),
    })
    void user_cannotGiveAKeyMoreThanTheyHold()
    {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "admin-key", "permissions", List.of(Permission.Names.API_APIKEY_ADMIN), "expiresAt", Instant.now().plus(Duration.ofDays(1)).toString()))
            .when().post("/api/keys")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "keeper", roles = "wombat-key-managers", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "iss", value = "https://idp"),
        @Claim(key = "sub", value = "keeper"),
    })
    void apiKeyManager_listsAndRevokesEveryKey()
    {
        ApiKeyService.Issued other = this.issue(admin(), List.of(Permission.Names.API_IMPACT_READ));

        given().when().get("/api/keys?all=true").then()
            .statusCode(200)
            .body("payload.id", hasItem(other.key().id));

        given().when().delete("/api/keys/" + other.key().id).then().statusCode(200);
        given().header(ApiKeyAuthenticationMechanism.HEADER, other.token()).when().get("/api/impact/environments").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "keeper", roles = "wombat-key-managers", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "iss", value = "https://idp"),
        @Claim(key = "sub", value = "keeper"),
    })
    void apiKeyManager_createsKeysWithTheUserReads()
    {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "reader", "permissions", List.of(Permission.Names.API_IMPACT_READ), "expiresAt", Instant.now().plus(Duration.ofDays(1)).toString()))
            .when().post("/api/keys")
            .then()
            .statusCode(200);
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
