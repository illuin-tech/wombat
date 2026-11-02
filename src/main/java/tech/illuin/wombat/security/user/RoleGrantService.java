package tech.illuin.wombat.security.user;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Role;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Roles granted by admins from the users page. Every request needs its caller's grants, so they are all held in memory:
 * reloaded after each change here, and at least every {@link #RELOAD_INTERVAL} so other replicas' changes show up too.
 */
@ApplicationScoped
public class RoleGrantService
{
    static final Duration RELOAD_INTERVAL = Duration.ofMinutes(1);

    private static final Logger logger = LoggerFactory.getLogger(RoleGrantService.class);

    private final RoleGrantRepository grants;
    private final UserRepository users;
    private final UserService userService;

    private volatile Snapshot snapshot;

    public RoleGrantService(RoleGrantRepository grants, UserRepository users, UserService userService)
    {
        this.grants = grants;
        this.users = users;
        this.userService = userService;
    }

    /** True when {@link #of} would answer from missing or outdated grants: call {@link #reloadIfStale()} first, off the event loop. */
    public boolean isStale()
    {
        Snapshot current = this.snapshot;
        return current == null || current.loadedAt().plus(RELOAD_INTERVAL).isBefore(Instant.now());
    }

    /** Blocking. On failure the previous grants stay in use (none before the first load) until the next interval. */
    public synchronized void reloadIfStale()
    {
        if (!this.isStale())
            return;
        try {
            this.reload();
        }
        catch (RuntimeException e) {
            logger.warn("Could not load the roles granted in Wombat, using the previous ones", e);
            Snapshot previous = this.snapshot;
            this.snapshot = new Snapshot(previous == null ? Map.of() : previous.byIdentity(), Instant.now());
        }
    }

    /** The roles granted to this identity, from memory. */
    public Set<Role> of(String issuer, String subject)
    {
        Snapshot current = this.snapshot;
        if (current == null)
            return Set.of();
        return current.byIdentity().getOrDefault(key(issuer, subject), Set.of());
    }

    /** The roles granted to each user, by user id, for the users page. */
    public Map<String, Map<Role, RoleGrantEntity>> byUser()
    {
        return QuarkusTransaction.requiringNew().call(() -> {
            Map<String, Map<Role, RoleGrantEntity>> byUser = new HashMap<>();
            for (RoleGrantEntity grant : this.grants.listAll())
                Role.fromValue(grant.role).ifPresent(role -> byUser.computeIfAbsent(grant.userId, id -> new HashMap<>()).put(role, grant));
            return byUser;
        });
    }

    public void grant(Caller admin, String userId, String roleValue)
    {
        Role role = role(roleValue);
        QuarkusTransaction.requiringNew().run(() -> {
            UserEntity user = this.users.findByIdOptional(userId).orElseThrow(NotFoundException::new);
            if (this.grants.findByUserAndRole(userId, role.value()).isPresent())
                return;

            RoleGrantEntity grant = new RoleGrantEntity();
            grant.userId = user.id;
            grant.role = role.value();
            grant.grantedBy = this.userService.ensure(admin).id;
            grant.persist();
            logger.info("{} granted role {} to {}", admin.name(), role.value(), user.name);
        });
        this.reload();
    }

    public void revoke(Caller admin, String userId, String roleValue)
    {
        Role role = role(roleValue);
        QuarkusTransaction.requiringNew().run(() -> {
            UserEntity user = this.users.findByIdOptional(userId).orElseThrow(NotFoundException::new);
            if (role == Role.ADMIN && user.issuer.equals(admin.issuer()) && user.subject.equals(admin.subject()))
                throw new ClientErrorException("You cannot remove your own admin role", Response.Status.CONFLICT);

            this.grants.findByUserAndRole(userId, role.value()).ifPresent(grant -> {
                grant.delete();
                logger.info("{} removed role {} from {}", admin.name(), role.value(), user.name);
            });
        });
        this.reload();
    }

    private void reload()
    {
        Map<String, Set<Role>> byIdentity = new HashMap<>();
        List<Object[]> rows = QuarkusTransaction.requiringNew().call(this.grants::findAllWithIdentity);
        for (Object[] row : rows)
            Role.fromValue((String) row[2]).ifPresent(role -> byIdentity.computeIfAbsent(key((String) row[0], (String) row[1]), k -> EnumSet.noneOf(Role.class)).add(role));
        this.snapshot = new Snapshot(Map.copyOf(byIdentity), Instant.now());
    }

    private static Role role(String value)
    {
        return Role.fromValue(value).orElseThrow(() -> new BadRequestException("Unknown role " + value));
    }

    private static String key(String issuer, String subject)
    {
        return issuer + '\n' + subject;
    }

    private record Snapshot(Map<String, Set<Role>> byIdentity, Instant loadedAt) {}
}
