package tech.illuin.wombat.security.user;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Role;
import tech.illuin.wombat.ui.AuthenticationEnabledTest;

import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(AuthenticationEnabledTest.AuthenticationEnabledProfile.class)
class UserActivityTest
{
    @Inject
    UserActivity activity;

    @Inject
    UserRepository users;

    @Test
    void newUser_isDueAndRecorded()
    {
        Caller caller = caller(UUID.randomUUID().toString(), Role.USER);

        assertTrue(this.activity.isDue(caller));
        this.activity.record(caller);

        UserEntity user = QuarkusTransaction.requiringNew().call(() -> this.users.findByIdentity("https://idp", caller.subject()).orElseThrow());
        assertEquals(EnumSet.of(Role.USER), user.roles);
        assertEquals("someone@example.com", user.email);
    }

    @Test
    void recordedUser_isNotDueAgainUntilTheirRolesChange()
    {
        String subject = UUID.randomUUID().toString();
        this.activity.record(caller(subject, Role.USER));

        assertFalse(this.activity.isDue(caller(subject, Role.USER)));
        assertTrue(this.activity.isDue(caller(subject, Role.ADMIN)));

        this.activity.record(caller(subject, Role.ADMIN));
        UserEntity user = QuarkusTransaction.requiringNew().call(() -> this.users.findByIdentity("https://idp", subject).orElseThrow());
        assertEquals(EnumSet.of(Role.ADMIN), user.roles);
    }

    private static Caller caller(String subject, Role role)
    {
        return new Caller("https://idp", subject, "someone", "someone@example.com", EnumSet.of(role), role.permissions());
    }
}
