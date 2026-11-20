package tech.illuin.wombat.engine.module.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Set;

/**
 * The heartbeat of an instance: the core deployment it runs, and the modules it detects.
 */
@Entity
@Table(name = "core_instances")
public class CoreInstanceEntity
{
    @Id @Column(name = "instance_id", nullable = false)
    public String instanceId;

    @Column(name = "deployed_version", nullable = false)
    public long deployedVersion;

    @Column(name = "deployed_at", nullable = false, columnDefinition = "INTEGER")
    public Instant deployedAt;

    /** Whether the latest deployment failed to build here; the instance then keeps running its deployed version. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "TEXT")
    public InstanceStatus status;

    @Column
    public String error;

    @Convert(converter = TypeNameSetConverter.class)
    @Column(name = "detected_modules", nullable = false)
    public Set<String> detectedModules;

    /** The asset-types its deployment requested, but no module detected here provides. */
    @Convert(converter = TypeNameSetConverter.class)
    @Column(name = "missing_modules", nullable = false)
    public Set<String> missingModules;

    @Column(name = "heartbeat_at", nullable = false, columnDefinition = "INTEGER")
    public Instant heartbeatAt;

    public enum InstanceStatus
    {
        ok,
        failed,
    }
}
