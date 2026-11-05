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

import static io.restassured.RestAssured.given;
import static io.restassured.matcher.RestAssuredMatchers.detailedCookie;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Google, set up as documented: no roles (emails or {@code "*"} map them), restricted by email domain, claims also read from userinfo,
 * and no {@code end_session_endpoint}.
 */
@QuarkusTest
@TestProfile(GoogleOidcTest.GoogleProfile.class)
class GoogleOidcTest
{
    private static final String ISSUER = "https://accounts.google.com";

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
    void workspaceUser_signsInAsUser()
    {
        String subject = "108011234567890123456";
        Map<String, String> session = this.signIn(idToken(subject, "alice@example.com", true));

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
    void emailListedForAdmin_signsInAsAdmin()
    {
        Map<String, String> session = this.signIn(idToken("108019999999999999999", "Boss@Example.com", true));

        given().cookies(session).when().get("/api/keys").then().statusCode(200);
        given().cookies(session).when().get("/users").then().statusCode(not(anyOf(is(401), is(403))));
        given().cookies(session).when().get("/api/users").then().statusCode(200);
    }

    @Test
    void emailOnlyInUserInfo_stillCounts()
    {
        Map<String, Object> idToken = idToken("108012222222222222222", "boss@example.com", true);
        Map<String, Object> userInfo = new HashMap<>(idToken);
        idToken.keySet().removeAll(List.of("email", "email_verified", "hd", "name"));

        Map<String, String> session = this.provider.signIn(MockProvider.sign(idToken), "ya29.opaque-access-token", userInfo);

        given().cookies(session).when().get("/api/keys").then().statusCode(200);
    }

    @Test
    void emailOutsideAllowedDomains_isForbidden()
    {
        Map<String, Object> idToken = idToken("108013333333333333333", "mallory@gmail.com", true);
        idToken.remove("hd");
        Map<String, String> session = this.signIn(idToken);

        given().cookies(session).when().get("/api/environments").then().statusCode(403);
    }

    @Test
    void unverifiedEmail_isForbidden()
    {
        Map<String, String> session = this.signIn(idToken("108014444444444444444", "boss@example.com", false));

        given().cookies(session).when().get("/api/environments").then().statusCode(403);
    }

    @Test
    void logout_onlyClearsTheWombatSession()
    {
        Map<String, String> session = this.signIn(idToken("108015555555555555555", "alice@example.com", true));

        given().cookies(session).redirects().follow(false)
            .when().get("/logout")
            .then()
            .statusCode(303)
            .header("Location", endsWith("/signed-out?idp=false"))
            .cookie("q_session", detailedCookie().maxAge(0));
    }

    private Map<String, String> signIn(Map<String, Object> idToken)
    {
        return this.provider.signIn(MockProvider.sign(idToken), "ya29.opaque-access-token", new HashMap<>(idToken));
    }

    /** Claims of a Google ID token requested with the {@code openid email profile} scopes. */
    private static Map<String, Object> idToken(String subject, String email, boolean verified)
    {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", ISSUER);
        claims.put("azp", MockProvider.CLIENT_ID);
        claims.put("aud", MockProvider.CLIENT_ID);
        claims.put("sub", subject);
        claims.put("hd", "example.com");
        claims.put("email", email);
        claims.put("email_verified", verified);
        claims.put("at_hash", "HK6E_P6Dh8Y93mRNtsDB1Q");
        claims.put("name", "Some One");
        claims.put("given_name", "Some");
        claims.put("family_name", "One");
        claims.put("picture", "https://lh3.googleusercontent.com/a/photo");
        return claims;
    }

    public static class GoogleProfile implements QuarkusTestProfile
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
            // Google's discovery document has no end_session_endpoint
            config.put("quarkus.oidc.discovery-enabled", "false");
            config.put("quarkus.oidc.authorization-path", "/");
            config.put("quarkus.oidc.token-path", "token");
            config.put("quarkus.oidc.jwks-path", "protocol/openid-connect/certs");
            config.put("quarkus.oidc.user-info-path", "protocol/openid-connect/userinfo");
            config.put("quarkus.oidc.authentication.user-info-required", "true");
            config.put("auth.roles.admin", "boss@example.com");
            config.put("auth.roles.user", "*");
            config.put("auth.allowed-email-domains", "example.com");
            return config;
        }
    }
}
