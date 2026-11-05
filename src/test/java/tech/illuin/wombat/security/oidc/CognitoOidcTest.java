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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** AWS Cognito user pools: groups read from {@code cognito:groups}, access tokens carry no {@code aud} and no email. */
@QuarkusTest
@TestProfile(CognitoOidcTest.CognitoProfile.class)
class CognitoOidcTest
{
    private static final String ISSUER = "https://cognito-idp.eu-west-3.amazonaws.com/eu-west-3_AbCdEf123";

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
    void groupMember_signsInAsUser()
    {
        String subject = UUID.randomUUID().toString();
        Map<String, String> session = this.signIn(subject, "alice@example.com", List.of("wombat-users"));

        given().cookies(session).when().get("/api/environments").then().statusCode(200);
        given().cookies(session).when().get("/api/keys?all=true").then().statusCode(403);
        given().cookies(session).when().get("/").then().statusCode(not(anyOf(is(401), is(403))));
        given().cookies(session).when().get("/api/app").then()
            .statusCode(200)
            .body("payload.user.name", is("alice@example.com"))
            .body("payload.user.roles", hasItems("user"));

        UserEntity user = MockProvider.awaitUser(this.users, ISSUER, subject);
        assertEquals(EnumSet.of(Role.USER), user.roles);
        assertEquals("alice@example.com", user.email);
    }

    @Test
    void adminGroupMember_signsInAsAdmin()
    {
        Map<String, String> session = this.signIn(UUID.randomUUID().toString(), "boss@example.com", List.of("wombat-users", "wombat-admins"));

        given().cookies(session).when().get("/api/keys").then().statusCode(200);
        given().cookies(session).when().get("/users").then().statusCode(not(anyOf(is(401), is(403))));
        given().cookies(session).when().get("/api/users").then().statusCode(200);
    }

    @Test
    void userInNoGroup_isForbidden()
    {
        Map<String, Object> idToken = idToken(UUID.randomUUID().toString(), "mallory@example.com", List.of());
        idToken.remove("cognito:groups");
        Map<String, String> session = this.provider.signIn(MockProvider.sign(idToken), MockProvider.sign(accessToken((String) idToken.get("sub"), List.of())));

        given().cookies(session).when().get("/api/environments").then().statusCode(403);
    }

    @Test
    void accessToken_reachesTheApiAsBearer()
    {
        String token = MockProvider.sign(accessToken(UUID.randomUUID().toString(), List.of("wombat-users")));

        given().auth().oauth2(token).when().get("/api/environments").then().statusCode(200);
        given().auth().oauth2(token).when().get("/api/keys?all=true").then().statusCode(403);
    }

    @Test
    void accessTokenFromAnotherUserPool_isRejected()
    {
        Map<String, Object> claims = accessToken(UUID.randomUUID().toString(), List.of("wombat-admins"));
        claims.put("iss", "https://cognito-idp.eu-west-3.amazonaws.com/eu-west-3_Other0000");

        given().auth().oauth2(MockProvider.sign(claims)).when().get("/api/environments").then().statusCode(401);
    }

    private Map<String, String> signIn(String subject, String email, List<String> groups)
    {
        return this.provider.signIn(MockProvider.sign(idToken(subject, email, groups)), MockProvider.sign(accessToken(subject, groups)));
    }

    /** Claims of a Cognito ID token requested with the {@code openid email profile} scopes. */
    private static Map<String, Object> idToken(String subject, String email, List<String> groups)
    {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", ISSUER);
        claims.put("sub", subject);
        claims.put("aud", MockProvider.CLIENT_ID);
        claims.put("token_use", "id");
        claims.put("cognito:username", subject);
        claims.put("cognito:groups", groups);
        claims.put("email", email);
        claims.put("email_verified", true);
        claims.put("origin_jti", UUID.randomUUID().toString());
        claims.put("event_id", UUID.randomUUID().toString());
        claims.put("auth_time", System.currentTimeMillis() / 1000);
        claims.put("at_hash", "Ap2cT6YhQTWn8BgyHxHn2w");
        return claims;
    }

    private static Map<String, Object> accessToken(String subject, List<String> groups)
    {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", ISSUER);
        claims.put("sub", subject);
        claims.put("client_id", MockProvider.CLIENT_ID);
        claims.put("token_use", "access");
        claims.put("scope", "openid email profile");
        claims.put("username", subject);
        claims.put("version", 2);
        claims.put("origin_jti", UUID.randomUUID().toString());
        claims.put("event_id", UUID.randomUUID().toString());
        claims.put("auth_time", System.currentTimeMillis() / 1000);
        if (!groups.isEmpty())
            claims.put("cognito:groups", groups);
        return claims;
    }

    public static class CognitoProfile implements QuarkusTestProfile
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
            config.put("quarkus.oidc.roles.role-claim-path", "cognito:groups");
            config.put("auth.roles.admin", "wombat-admins");
            config.put("auth.roles.user", "wombat-users");
            return config;
        }
    }
}
