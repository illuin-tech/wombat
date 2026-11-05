package tech.illuin.wombat.security.oidc;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWireMock;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.security.Role;
import tech.illuin.wombat.security.user.UserEntity;
import tech.illuin.wombat.security.user.UserRepository;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Keycloak, set up as documented: realm roles read from {@code realm_access/roles} in the access token, since with Keycloak's
 * default client scopes they are not in the ID token.
 */
@QuarkusTest
@TestProfile(KeycloakOidcTest.KeycloakProfile.class)
class KeycloakOidcTest
{
    private static final String ISSUER = "https://sso.example.com/realms/wombat";

    @OidcWireMock
    WireMockServer server;

    @Inject
    UserRepository users;

    private MockProvider provider;

    @BeforeEach
    void setUp()
    {
        this.provider = new MockProvider(this.server);
    }

    @Test
    void realmUser_signsInAsUser()
    {
        String subject = UUID.randomUUID().toString();
        Map<String, String> session = this.signIn(subject, "alice", List.of("wombat-users"));

        given().cookies(session).when().get("/api/environments").then().statusCode(200);
        given().cookies(session).when().get("/api/keys").then().statusCode(403);
        given().cookies(session).when().get("/").then().statusCode(not(anyOf(is(401), is(403))));
        given().cookies(session).when().get("/api/app").then()
            .statusCode(200)
            .body("payload.user.name", is("alice@example.com"))
            .body("payload.user.roles", hasItems("user"));

        UserEntity user = MockProvider.awaitUser(this.users, ISSUER, subject);
        assertEquals(EnumSet.of(Role.USER), user.roles);
    }

    @Test
    void realmAdmin_signsInAsAdmin()
    {
        Map<String, String> session = this.signIn(UUID.randomUUID().toString(), "boss", List.of("wombat-admins"));

        given().cookies(session).when().get("/api/keys").then().statusCode(200);
        given().cookies(session).when().get("/users").then().statusCode(not(anyOf(is(401), is(403))));
        given().cookies(session).when().get("/api/users").then().statusCode(200);
    }

    @Test
    void userWithOnlyDefaultRoles_isForbidden()
    {
        Map<String, String> session = this.signIn(UUID.randomUUID().toString(), "mallory", List.of());

        given().cookies(session).when().get("/api/environments").then().statusCode(403);
    }

    @Test
    void accessToken_reachesTheApiAsBearer()
    {
        String token = MockProvider.sign(accessToken(UUID.randomUUID().toString(), "ci", List.of("wombat-users")));

        given().auth().oauth2(token).when().get("/api/environments").then().statusCode(200);
        given().auth().oauth2(token).when().get("/api/keys").then().statusCode(403);
    }

    @Test
    void accessTokenFromAnotherRealm_isRejected()
    {
        Map<String, Object> claims = accessToken(UUID.randomUUID().toString(), "ci", List.of("wombat-admins"));
        claims.put("iss", "https://sso.example.com/realms/other");

        given().auth().oauth2(MockProvider.sign(claims)).when().get("/api/environments").then().statusCode(401);
    }

    @Test
    void logout_endsTheKeycloakSession()
    {
        Map<String, String> session = this.signIn(UUID.randomUUID().toString(), "alice", List.of("wombat-users"));

        given().cookies(session).redirects().follow(false)
            .when().get("/logout")
            .then()
            .statusCode(303)
            .header("Location", startsWith(this.server.baseUrl() + MockProvider.REALM_PATH + "/protocol/openid-connect/end-session"))
            .header("Location", containsString("id_token_hint="))
            .header("Location", containsString("client_id=" + MockProvider.CLIENT_ID));
    }

    private Map<String, String> signIn(String subject, String username, List<String> realmRoles)
    {
        return this.provider.signIn(MockProvider.sign(idToken(subject, username)), MockProvider.sign(accessToken(subject, username, realmRoles)));
    }

    /** Claims of a Keycloak ID token with the default {@code profile}, {@code email} and {@code roles} client scopes. */
    private static Map<String, Object> idToken(String subject, String username)
    {
        Map<String, Object> claims = identity(subject, username);
        claims.put("typ", "ID");
        claims.put("aud", MockProvider.CLIENT_ID);
        claims.put("at_hash", "2s1o7mOBl3Q2wd4lXvYlmA");
        return claims;
    }

    private static Map<String, Object> accessToken(String subject, String username, List<String> realmRoles)
    {
        List<String> roles = new ArrayList<>(realmRoles);
        roles.addAll(List.of("default-roles-wombat", "offline_access", "uma_authorization"));

        Map<String, Object> claims = identity(subject, username);
        claims.put("typ", "Bearer");
        claims.put("aud", "account");
        claims.put("scope", "openid email profile");
        claims.put("allowed-origins", List.of("https://wombat.example.com"));
        claims.put("realm_access", Map.of("roles", roles));
        claims.put("resource_access", Map.of("account", Map.of("roles", List.of("manage-account", "view-profile"))));
        return claims;
    }

    private static Map<String, Object> identity(String subject, String username)
    {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", ISSUER);
        claims.put("sub", subject);
        claims.put("azp", MockProvider.CLIENT_ID);
        claims.put("sid", UUID.randomUUID().toString());
        claims.put("acr", "1");
        claims.put("email_verified", true);
        claims.put("name", username + " Doe");
        claims.put("preferred_username", username);
        claims.put("given_name", username);
        claims.put("family_name", "Doe");
        claims.put("email", username + "@example.com");
        return claims;
    }

    public static class KeycloakProfile implements QuarkusTestProfile
    {
        @Override
        public String getConfigProfile()
        {
            return "test,oidc";
        }

        @Override
        public List<TestResourceEntry> testResources()
        {
            return List.of(new TestResourceEntry(OidcWiremockTestResource.class));
        }

        @Override
        public Map<String, String> getConfigOverrides()
        {
            Map<String, String> config = new HashMap<>(MockProvider.tenantConfig(ISSUER));
            config.put("quarkus.oidc.roles.role-claim-path", "realm_access/roles");
            // realm roles are not in Keycloak's ID tokens by default, which Quarkus reads roles from on browser sign-ins
            config.put("quarkus.oidc.roles.source", "accesstoken");
            config.put("auth.roles.admin", "wombat-admins");
            config.put("auth.roles.user", "wombat-users");
            return config;
        }
    }
}
