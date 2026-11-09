package tech.illuin.wombat.endpoint.secret;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.security.PermissionAugmentor;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
@TestProfile(AuthenticationEnabledTest.AuthenticationEnabledProfile.class)
class SecretControllerDisabledStoreTest
{
    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "email", value = "boss@example.com"))
    void endpointsAreUnavailableWhenDatabaseStoreIsDisabled() throws Exception
    {
        given().when().get("/api/secrets").then().statusCode(404);
        given().contentType(ContentType.JSON).body("{\"key\":\"key\",\"value\":\"value\"}").when().put("/api/secrets").then().statusCode(404);
        given().queryParam("key", "key").when().delete("/api/secrets").then().statusCode(404);
        given().when().post("/api/secrets/database/rewrap").then().statusCode(404);

        String document = given().accept(ContentType.JSON).when().get("/api/openapi").then().statusCode(200).extract().asString();
        JsonNode openApi = new ObjectMapper().readTree(document);
        assertFalse(openApi.path("paths").has("/api/secrets"));
        assertFalse(openApi.path("paths").has("/api/secrets/database/rewrap"));
        assertFalse(openApi.path("components").path("schemas").has("SecretWriteRequest"));
        assertFalse(openApi.path("components").path("schemas").has("SecretRewrap"));
    }

}