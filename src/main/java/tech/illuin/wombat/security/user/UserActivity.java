package tech.illuin.wombat.security.user;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Role;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Records signed-in users from their requests, for the users page. Remembering what was last written keeps the database
 * out of the request path: a user is written on their first request, when their name, email or roles change, and then
 * at most once per {@link #RECORD_INTERVAL}.
 */
@ApplicationScoped
public class UserActivity
{
    static final Duration RECORD_INTERVAL = Duration.ofMinutes(5);

    private static final Logger logger = LoggerFactory.getLogger(UserActivity.class);

    private final UserService userService;
    private final ConcurrentMap<String, Recorded> recorded = new ConcurrentHashMap<>();

    public UserActivity(UserService userService)
    {
        this.userService = userService;
    }

    public boolean isDue(Caller caller)
    {
        Recorded last = this.recorded.get(key(caller));
        return last == null
            || last.at().plus(RECORD_INTERVAL).isBefore(Instant.now())
            || !last.roles().equals(caller.roles())
            || !Objects.equals(last.name(), caller.name())
            || !Objects.equals(last.email(), caller.email());
    }

    /** Blocking. A failure is logged, never thrown: it must not deny the request it was recorded from. */
    public void record(Caller caller)
    {
        try {
            this.userService.record(caller);
            this.recorded.put(key(caller), new Recorded(Instant.now(), Set.copyOf(caller.roles()), caller.name(), caller.email()));
        }
        catch (RuntimeException e) {
            logger.warn("Could not record user {}", caller.name(), e);
        }
    }

    private static String key(Caller caller)
    {
        return caller.issuer() + '\n' + caller.subject();
    }

    private record Recorded(Instant at, Set<Role> roles, String name, String email) {}
}
