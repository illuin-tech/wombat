package tech.illuin.wombat.security.user;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Role;

import java.util.EnumSet;
import java.util.List;

import static tech.illuin.wombat.engine.context.persistence.EnvironmentEntity.now;

@ApplicationScoped
public class UserService
{
    private final UserRepository users;

    public UserService(UserRepository users)
    {
        this.users = users;
    }

    /**
     * Creates or refreshes the caller's row: name, email and roles as of now. {@code caller.roles()} must be the roles
     * mapped from the identity provider and {@code auth.roles.*} only, without the ones granted in Wombat.
     */
    @Transactional
    public UserEntity record(Caller caller)
    {
        UserEntity user = this.findOrNew(caller);
        user.name = caller.name();
        user.email = caller.email();
        user.roles = caller.roles().isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(caller.roles());
        user.lastSeenAt = now();
        if (!user.isPersistent())
            user.persist();
        return user;
    }

    /**
     * The caller's row, created if missing but never overwritten: for recording who owns, created or granted something. A
     * new row takes the caller's roles, which can only be mapped ones since nothing can be granted to a user without a row.
     */
    @Transactional
    public UserEntity ensure(Caller caller)
    {
        UserEntity user = this.findOrNew(caller);
        if (!user.isPersistent())
        {
            user.name = caller.name();
            user.email = caller.email();
            user.roles = caller.roles().isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(caller.roles());
            user.persist();
        }
        return user;
    }

    private UserEntity findOrNew(Caller caller)
    {
        return this.users.findByIdentity(caller.issuer(), caller.subject()).orElseGet(() -> {
            UserEntity created = new UserEntity();
            created.issuer = caller.issuer();
            created.subject = caller.subject();
            return created;
        });
    }

    @Transactional
    public List<UserEntity> list()
    {
        return this.users.findAllByName();
    }
}
