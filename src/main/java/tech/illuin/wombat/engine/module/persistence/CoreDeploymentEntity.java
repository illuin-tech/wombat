package tech.illuin.wombat.engine.module.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * A core redeploy: the modules every instance builds its core of, as they were enabled when it was requested. Instances
 * converge on the latest version.
 */
@Entity
@Table(name = "core_deployments")
public class CoreDeploymentEntity
{
    /** Orders the deployments: a redeploy takes the next one. */
    @Id @Column(nullable = false)
    public Long version;

    /** Hex SHA-256 of the version, request time and modules; shortened to 7 characters for display, like a commit id. */
    @Column(nullable = false, unique = true)
    public String id;

    @Column(name = "requested_at", nullable = false, columnDefinition = "INTEGER")
    public Instant requestedAt;

    /** Null for the deployment of the first startup, or when authentication is disabled. */
    @Column(name = "requested_by")
    public String requestedBy;

    /**
     * In the order the core is built in, which is the order the family modules of a family are tried in. The deployments
     * recorded before that order was kept list their modules by name: they hold no family module, so it is harmless.
     */
    @Convert(converter = TypeNameListConverter.class)
    @Column(name = "enabled_modules", nullable = false)
    public List<String> enabledModules;

    public static CoreDeploymentEntity of(long version, Instant requestedAt, String requestedBy, List<String> enabledModules)
    {
        CoreDeploymentEntity entity = new CoreDeploymentEntity();
        entity.version = version;
        entity.requestedAt = requestedAt;
        entity.requestedBy = requestedBy;
        entity.enabledModules = new ArrayList<>(enabledModules);
        entity.id = id(version, requestedAt, entity.enabledModules);
        return entity;
    }

    /* The version is part of it, so that two deployments of the same modules still get different ids */
    static String id(long version, Instant requestedAt, List<String> enabledModules)
    {
        String content = version + "\n" + requestedAt.toEpochMilli() + "\n" + String.join(",", enabledModules);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
