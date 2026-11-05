package tech.illuin.wombat.endpoint.access;

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
import tech.illuin.wombat.security.user.RoleGrantService;
import tech.illuin.wombat.security.user.UserEntity;
import tech.illuin.wombat.security.user.UserService;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import java.util.EnumSet;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@TestProfile(AuthenticationEnabledTest.AuthenticationEnabledProfile.class)
class UserControllerTest
{
    @Inject UserService users;
    @Inject RoleGrantService roleGrants;

    @Test
    @TestSecurity(user = "boss", roles = "wombat-admins", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "email", value = "boss@example.com"))
    void list_includesProviderAndGrantedRoleSources()
    {
        given()
            .when().get("/api/users")
            .then()
            .statusCode(200);

        UserEntity admin = this.users.list().stream()
            .filter(user -> "boss@example.com".equals(user.email))
            .findFirst()
            .orElseThrow();
        Caller selfGrantor = new Caller(
            admin.issuer,
            admin.subject,
            admin.name,
            admin.email,
            EnumSet.of(Role.ADMIN),
            Role.ADMIN.permissions()
        );
        this.roleGrants.grant(selfGrantor, admin.id, Role.ADMIN.value());

        String suffix = UUID.randomUUID().toString();
        UserEntity target = this.users.record(new Caller(
            "https://test-idp",
            "target-" + suffix,
            "Target User",
            "target-" + suffix + "@example.com",
            EnumSet.of(Role.USER),
            Role.USER.permissions()
        ));
        Caller grantor = new Caller(
            "https://test-idp",
            "grantor-" + suffix,
            "Grantor Name",
            "grantor-" + suffix + "@example.com",
            EnumSet.of(Role.ADMIN),
            Role.ADMIN.permissions()
        );
        this.roleGrants.grant(grantor, target.id, Role.ADMIN.value());

        given()
            .when().get("/api/users")
            .then()
            .statusCode(200)
            .body("payload.find { it.email == 'target-" + suffix + "@example.com' }.self", is(false))
            .body("payload.find { it.email == 'target-" + suffix + "@example.com' }.roles.find { it.role == 'user' }.fromProvider", is(true))
            .body("payload.find { it.email == 'target-" + suffix + "@example.com' }.roles.find { it.role == 'user' }.grant", nullValue())
            .body("payload.find { it.email == 'target-" + suffix + "@example.com' }.roles.find { it.role == 'user' }.removable", is(false))
            .body("payload.find { it.email == 'target-" + suffix + "@example.com' }.roles.find { it.role == 'admin' }.fromProvider", is(false))
            .body("payload.find { it.email == 'target-" + suffix + "@example.com' }.roles.find { it.role == 'admin' }.grant.grantedBy", is("Grantor Name"))
            .body("payload.find { it.email == 'target-" + suffix + "@example.com' }.roles.find { it.role == 'admin' }.grant.grantedAt", notNullValue())
            .body("payload.find { it.email == 'target-" + suffix + "@example.com' }.roles.find { it.role == 'admin' }.removable", is(true))
            .body("payload.find { it.email == 'boss@example.com' }.self", is(true))
            .body("payload.find { it.email == 'boss@example.com' }.roles.find { it.role == 'admin' }.fromProvider", is(true))
            .body("payload.find { it.email == 'boss@example.com' }.roles.find { it.role == 'admin' }.grant.grantedAt", notNullValue())
            .body("payload.find { it.email == 'boss@example.com' }.roles.find { it.role == 'admin' }.removable", is(false));
    }

    @Test
    @TestSecurity(user = "someone", roles = "wombat-users", augmentors = PermissionAugmentor.class)
    @OidcSecurity(claims = @Claim(key = "email", value = "someone@example.com"))
    void list_isForbiddenForUsersWithoutTheReadPermission()
    {
        given()
            .when().get("/api/users")
            .then()
            .statusCode(403);
    }
}
