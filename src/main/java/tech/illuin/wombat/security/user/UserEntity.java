package tech.illuin.wombat.security.user;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import tech.illuin.wombat.security.Role;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static tech.illuin.wombat.engine.context.persistence.EnvironmentEntity.now;

/**
 * A user who signed in at least once. {@code roles} are the ones mapped from the identity provider and {@code auth.roles.*}
 * when last seen (see {@link UserActivity}); roles granted in Wombat are in {@link RoleGrantEntity}.
 */
@Entity
@Table(name = "users")
public class UserEntity extends PanacheEntityBase
{
    @Id @Column(nullable = false)
    public String id;

    @Column(nullable = false)
    public String issuer;

    @Column(nullable = false)
    public String subject;

    @Column
    public String name;

    @Column
    public String email;

    @Convert(converter = RoleSetConverter.class)
    @Column(nullable = false)
    public Set<Role> roles = EnumSet.noneOf(Role.class);

    @Column(name = "created_at", nullable = false, columnDefinition = "INTEGER")
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "INTEGER")
    public Instant updatedAt;

    @Column(name = "last_seen_at", nullable = false, columnDefinition = "INTEGER")
    public Instant lastSeenAt;

    @PrePersist
    void onPersist()
    {
        if (this.id == null)
            this.id = UUID.randomUUID().toString();
        this.createdAt = now();
        this.updatedAt = this.createdAt;
        if (this.lastSeenAt == null)
            this.lastSeenAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate()
    {
        this.updatedAt = now();
    }
}
