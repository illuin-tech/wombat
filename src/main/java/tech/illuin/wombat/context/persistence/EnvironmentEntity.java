package tech.illuin.wombat.context.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "environments")
public class EnvironmentEntity extends PanacheEntityBase
{

    @Id
    @Column(nullable = false)
    public String id;

    @Column(nullable = false, unique = true)
    public String uuid;

    @Column(nullable = false)
    public String name;

    @Column(name = "created_at", nullable = false, columnDefinition = "INTEGER")
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "INTEGER")
    public Instant updatedAt;

    @Column(name = "disabled_at", columnDefinition = "INTEGER")
    public Instant disabledAt;

    @PrePersist
    void onPersist()
    {
        if (this.uuid == null)
            this.uuid = UUID.randomUUID().toString();
        this.createdAt = now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate()
    {
        this.updatedAt = now();
    }

    /**
     * SQLite stores timestamps as epoch millis, so anything finer would silently disappear on
     * the next read; truncating up front keeps in-memory entities identical to persisted ones.
     */
    public static Instant now()
    {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
