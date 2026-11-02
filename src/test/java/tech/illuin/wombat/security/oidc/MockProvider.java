package tech.illuin.wombat.security.oidc;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.restassured.response.Response;
import io.smallrye.jwt.build.Jwt;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.core.UriBuilder;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import tech.illuin.wombat.security.user.UserEntity;
import tech.illuin.wombat.security.user.UserRepository;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plays an identity provider on top of {@link io.quarkus.test.oidc.server.OidcWiremockTestResource}: issues provider-shaped tokens
 * signed with its key, and drives Wombat through a real authorization code flow.
 */
final class MockProvider
{
    static final String CLIENT_ID = "wombat";
    static final String CLIENT_SECRET = "secret";
    static final String REALM_PATH = "/auth/realms/quarkus";
    static final String TOKEN_PATH = REALM_PATH + "/token";
    static final String USER_INFO_PATH = REALM_PATH + "/protocol/openid-connect/userinfo";

    private final WireMockServer server;

    MockProvider(WireMockServer server)
    {
        this.server = server;
    }

    /** The configuration pointing the default tenant at the mock server, verifying tokens against {@code issuer}. */
    static Map<String, String> tenantConfig(String issuer)
    {
        return Map.of(
            "quarkus.oidc.auth-server-url", "${keycloak.url}/realms/quarkus",
            "quarkus.oidc.client-id", CLIENT_ID,
            "quarkus.oidc.credentials.secret", CLIENT_SECRET,
            "quarkus.oidc.token.issuer", issuer
        );
    }

    /** Signs {@code claims} as a token valid for the next five minutes. */
    static String sign(Map<String, Object> claims)
    {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> token = new HashMap<>(claims);
        token.putIfAbsent("iat", now);
        token.putIfAbsent("exp", now + 300);
        token.putIfAbsent("jti", UUID.randomUUID().toString());
        return Jwt.claims(token).jws().keyId("1").sign("privateKey.jwk");
    }

    /** Signs the user in through the authorization code flow, the provider answering with these tokens, and returns their session cookies. */
    Map<String, String> signIn(String idToken, String accessToken)
    {
        return this.signIn(idToken, accessToken, null);
    }

    /** Same as {@link #signIn(String, String)}, the provider also answering its userinfo endpoint with {@code userInfo}. */
    Map<String, String> signIn(String idToken, String accessToken, Map<String, Object> userInfo)
    {
        this.server.stubFor(post(urlEqualTo(TOKEN_PATH))
            .atPriority(1)
            .withRequestBody(containing("grant_type=authorization_code"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(new JsonObject()
                    .put("access_token", accessToken)
                    .put("id_token", idToken)
                    .put("token_type", "Bearer")
                    .put("expires_in", 300)
                    .encode())));
        if (userInfo != null)
            this.server.stubFor(get(urlEqualTo(USER_INFO_PATH))
                .atPriority(1)
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(new JsonObject(userInfo).encode())));

        Response challenge = given().redirects().follow(false)
            .when().get("/")
            .then().statusCode(302)
            .extract().response();
        URI authorization = URI.create(challenge.header("Location"));
        assertTrue(authorization.getPath().startsWith(REALM_PATH), "redirected to the provider: " + authorization);

        Map<String, String> query = query(authorization);
        assertEquals(CLIENT_ID, query.get("client_id"));
        assertEquals("code", query.get("response_type"));

        URI callback = UriBuilder.fromUri(query.get("redirect_uri"))
            .queryParam("state", query.get("state"))
            .queryParam("code", UUID.randomUUID().toString())
            .build();
        Response signedIn = given().redirects().follow(false)
            .cookies(challenge.cookies())
            .when().get(callback)
            .then().statusCode(302)
            .extract().response();

        Map<String, String> session = signedIn.cookies();
        assertTrue(session.keySet().stream().anyMatch(name -> name.startsWith("q_session")), "session cookie set: " + session.keySet());
        return session;
    }

    /** The user recorded by the sign-in listener, which runs asynchronously after the sign-in. */
    static UserEntity awaitUser(UserRepository users, String issuer, String subject)
    {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (true)
        {
            Optional<UserEntity> user = QuarkusTransaction.requiringNew().call(() -> users.findByIdentity(issuer, subject));
            if (user.isPresent())
                return user.get();
            if (System.nanoTime() > deadline)
                return fail("No user recorded for " + subject + " from " + issuer);
            LockSupport.parkNanos(Duration.ofMillis(50).toNanos());
        }
    }

    private static Map<String, String> query(URI uri)
    {
        Map<String, String> query = new HashMap<>();
        for (NameValuePair pair : URLEncodedUtils.parse(uri, StandardCharsets.UTF_8))
            query.put(pair.getName(), pair.getValue());
        return query;
    }
}
