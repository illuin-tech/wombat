package tech.illuin.wombat.security.apikey;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.Role;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
@TestProfile(NeverExpiringApiKeyTest.NeverExpiringProfile.class)
class NeverExpiringApiKeyTest
{
    @Inject
    ApiKeyService service;

    @Test
    void keyWithoutExpiry_authenticates()
    {
        Caller caller = new Caller("https://idp", UUID.randomUUID().toString(), "admin", null, EnumSet.of(Role.ADMIN), Role.ADMIN.permissions());
        ApiKeyService.Issued issued = this.service.create(caller, "forever", List.of(Permission.Names.API_ENVIRONMENT_READ), null);

        assertNull(issued.key().expiresAt);
        given().header(ApiKeyAuthenticationMechanism.HEADER, issued.token()).when().get("/api/environments").then().statusCode(200);
    }

    @Test
    void revokedKeyWithoutExpiry_isRejected()
    {
        Caller caller = new Caller("https://idp", UUID.randomUUID().toString(), "admin", null, EnumSet.of(Role.ADMIN), Role.ADMIN.permissions());
        ApiKeyService.Issued issued = this.service.create(caller, "forever", List.of(Permission.Names.API_ENVIRONMENT_READ), null);
        this.service.revoke(caller, issued.key().id);

        given().header(ApiKeyAuthenticationMechanism.HEADER, issued.token()).when().get("/api/environments").then().statusCode(401);
    }

    public static class NeverExpiringProfile extends AuthenticationEnabledTest.AuthenticationEnabledProfile
    {
        @Override
        public Map<String, String> getConfigOverrides()
        {
            Map<String, String> overrides = new HashMap<>(super.getConfigOverrides());
            overrides.put("auth.api-keys.allow-never-expiring", "true");
            return overrides;
        }
    }
}
