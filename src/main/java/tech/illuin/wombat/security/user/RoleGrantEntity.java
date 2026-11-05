package tech.illuin.wombat.security.user;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

import static tech.illuin.wombat.engine.context.persistence.EnvironmentEntity.now;

/** A Wombat role granted to a user by an admin, held on top of the roles mapped from the identity provider. */
@Entity
@Table(name = "role_grants")
public class RoleGrantEntity extends PanacheEntityBase
{
    @Id @Column(nullable = false)
    public String id;

    @Column(name = "user_id", nullable = false)
    public String userId;

    /** {@link tech.illuin.wombat.security.Role#value()} */
    @Column(nullable = false)
    public String role;

    @Column(name = "granted_by", nullable = false)
    public String grantedBy;

    @Column(name = "granted_at", nullable = false, columnDefinition = "INTEGER")
    public Instant grantedAt;

    @PrePersist
    void onPersist()
    {
        if (this.id == null)
            this.id = UUID.randomUUID().toString();
        this.grantedAt = now();
    }
}
