package tech.illuin.wombat.security.apikey;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import tech.illuin.wombat.security.Permission;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static tech.illuin.wombat.engine.context.persistence.EnvironmentEntity.now;

/** A key belongs to no one: it carries its own permissions, and {@code createdBy} only records which admin issued it. */
@Entity
@Table(name = "api_keys")
public class ApiKeyEntity extends PanacheEntityBase
{
    @Id @Column(nullable = false)
    public String id;

    @Column(nullable = false)
    public String name;

    @Convert(converter = PermissionSetConverter.class)
    @Column(nullable = false)
    public Set<Permission> permissions = EnumSet.noneOf(Permission.class);

    /** {@code users.id} of the admin who created the key. */
    @Column(name = "created_by", nullable = false)
    public String createdBy;

    @Column(name = "created_at", nullable = false, columnDefinition = "INTEGER")
    public Instant createdAt;

    @Column(name = "expires_at", columnDefinition = "INTEGER")
    public Instant expiresAt;

    @Column(name = "revoked_at", columnDefinition = "INTEGER")
    public Instant revokedAt;

    @Column(name = "last_used_at", columnDefinition = "INTEGER")
    public Instant lastUsedAt;

    @PrePersist
    void onPersist()
    {
        if (this.id == null)
            this.id = UUID.randomUUID().toString();
        this.createdAt = now();
    }

    public boolean isActive(Instant at)
    {
        return this.revokedAt == null && (this.expiresAt == null || this.expiresAt.isAfter(at));
    }
}
