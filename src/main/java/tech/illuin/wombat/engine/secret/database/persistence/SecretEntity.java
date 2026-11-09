package tech.illuin.wombat.engine.secret.database.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

import static tech.illuin.wombat.engine.context.persistence.EnvironmentEntity.now;

@Entity
@Table(name = "secrets")
public class SecretEntity extends PanacheEntityBase
{
    @Id
    @Column(name = "secret_key", nullable = false, columnDefinition = "TEXT")
    public String key;

    @Column(name = "payload_ciphertext", nullable = false)
    public byte[] ciphertext;

    @Column(name = "payload_nonce", nullable = false)
    public byte[] payloadNonce;

    @Column(name = "wrapped_dek", nullable = false)
    public byte[] wrappedDek;

    @Column(name = "wrapping_nonce", nullable = false)
    public byte[] wrappingNonce;

    @Column(name = "kek_id", nullable = false)
    public String kekId;

    @Column(name = "created_at", nullable = false, columnDefinition = "INTEGER")
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "INTEGER")
    public Instant updatedAt;

    @Version
    @Column(nullable = false)
    public long revision;

    @PrePersist
    void onPersist()
    {
        Instant now = now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate()
    {
        this.updatedAt = now();
    }
}
