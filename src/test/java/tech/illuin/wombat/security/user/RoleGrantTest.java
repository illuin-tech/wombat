package tech.illuin.wombat.security.user;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.PermissionAugmentor;
import tech.illuin.wombat.security.Role;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import java.util.EnumSet;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
@TestProfile(AuthenticationEnabledTest.AuthenticationEnabledProfile.class)
class RoleGrantTest
{
    @Inject
    UserService users;

    @Inject
    RoleGrantService grants;

    @Test
    @TestSecurity(user = "promoted", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "sub", value = "promoted"),
        @Claim(key = "email", value = "promoted@example.com"),
    })
    void grantedAdmin_reachesAdminPagesAtTheirNextRequest()
    {
        given().when().get("/users").then().statusCode(403);

        this.grants.grant(admin(), this.recorded("promoted").id, Role.ADMIN.value());

        given().when().get("/users").then().statusCode(200);
        given().when().get("/").then().statusCode(200).body(containsString("<span class=\"header-user-role\">admin</span>"));
    }

    @Test
    @TestSecurity(user = "demoted", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "sub", value = "demoted"),
    })
    void removedGrant_isLostAtTheNextRequest()
    {
        String id = this.recorded("demoted").id;
        this.grants.grant(admin(), id, Role.ADMIN.value());
        given().when().get("/users").then().statusCode(200);

        this.grants.revoke(admin(), id, Role.ADMIN.value());
        given().when().get("/users").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "self-admin", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "sub", value = "self-admin"),
    })
    void admin_cannotRemoveTheirOwnAdminRole()
    {
        String id = this.recorded("self-admin").id;
        this.grants.grant(admin(), id, Role.ADMIN.value());

        given().when().delete("/api/users/" + id + "/roles/admin").then().statusCode(409);
        given().when().get("/users").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "sub", value = "boss"),
        @Claim(key = "email", value = "boss@example.com"),
    })
    void admin_grantsAndRemovesRolesFromTheApi()
    {
        String subject = UUID.randomUUID().toString();
        String id = this.recorded(subject).id;

        given().when().put("/api/users/" + id + "/roles/admin").then().statusCode(200);
        given().when().get("/users").then().statusCode(200)
            .body(containsString("data-user-id=\"" + id + "\" data-user-name=\"" + subject + "\" data-role=\"admin\" aria-label"))
            .body(containsString("Granted in Wombat by"));
        given().when().delete("/api/users/" + id + "/roles/admin").then().statusCode(200);

        given().when().put("/api/users/" + id + "/roles/owner").then().statusCode(400);
        given().when().put("/api/users/" + UUID.randomUUID() + "/roles/admin").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "someone", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = {
        @Claim(key = "sub", value = "someone"),
    })
    void user_cannotGrantRoles()
    {
        String id = this.recorded(UUID.randomUUID().toString()).id;

        given().when().put("/api/users/" + id + "/roles/admin").then().statusCode(403);
    }

    private UserEntity recorded(String subject)
    {
        return this.users.record(new Caller("", subject, subject, null, EnumSet.of(Role.USER), Role.USER.permissions()));
    }

    private static Caller admin()
    {
        return new Caller("https://idp", UUID.randomUUID().toString(), "admin", null, EnumSet.of(Role.ADMIN), Role.ADMIN.permissions());
    }
}
