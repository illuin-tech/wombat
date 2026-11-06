package tech.illuin.wombat.engine.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.engine.context.persistence.AssetConfigAction;
import tech.illuin.wombat.engine.context.persistence.AssetConfigHistory;
import tech.illuin.wombat.engine.context.persistence.AssetConfigHistoryRepository;
import tech.illuin.wombat.engine.context.persistence.AssetEntity;
import tech.illuin.wombat.engine.context.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.persistence.EnvironmentEntity;
import tech.illuin.wombat.engine.context.persistence.EnvironmentRepository;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.engine.monitor.MonitoredEnvironments;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Makes the persisted environments/assets match their declarations: the monitored-environments YAML at every startup,
 * and the environments pushed by each worker to an aggregator. Declarations are the source of truth, the DB their
 * persisted, historized projection.
 * <p>
 * Each asset belongs to the declaration it comes from (its {@link AssetEntity#owner}), and a reconciliation only
 * creates, updates and soft-deletes the assets of its own owner, so the YAML and the workers can share an environment.
 * An environment is disabled once nothing declares it anymore and none of its assets is left. Every asset change
 * appends a row to {@link AssetConfigHistory}.
 */
@ApplicationScoped
public class EnvironmentReconciler
{
    static final int STARTUP_PRIORITY_RECONCILE = 2000;

    public static final String LOCAL_OWNER = null;
    private static final Logger logger = LoggerFactory.getLogger(EnvironmentReconciler.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final EnvironmentRepository repository;
    private final AssetRepository assetRepository;
    private final AssetConfigHistoryRepository historyRepository;
    private final MonitoredEnvironments monitoredEnvironments;

    public EnvironmentReconciler(
        EnvironmentRepository repository,
        AssetRepository assetRepository,
        AssetConfigHistoryRepository historyRepository,
        MonitoredEnvironments monitoredEnvironments
    ) {
        this.repository = repository;
        this.assetRepository = assetRepository;
        this.historyRepository = historyRepository;
        this.monitoredEnvironments = monitoredEnvironments;
    }

    @Transactional
    void onStart(@Observes @Priority(STARTUP_PRIORITY_RECONCILE) StartupEvent event)
    {
        Result result = this.reconcile(LOCAL_OWNER, this.monitoredEnvironments.environments());
        logger.info("Environment reconciliation complete: {} created, {} updated, {} deleted assets", result.created(), result.updated(), result.deleted());
    }

    @Transactional
    public Result reconcile(String owner, Map<String, Environment> desired)
    {
        int created = 0;
        int updated = 0;

        Set<String> desiredAssetIds = new HashSet<>();
        for (Map.Entry<String, Environment> entry : desired.entrySet())
        {
            this.reconcileEnvironment(entry.getKey(), entry.getValue());
            for (Asset asset : entry.getValue().assets())
            {
                desiredAssetIds.add(asset.identity().id());
                AssetConfigAction action = this.reconcileAsset(owner, entry.getKey(), asset);
                if (action == AssetConfigAction.CREATE)
                    created++;
                else if (action == AssetConfigAction.UPDATE)
                    updated++;
            }
        }

        List<AssetEntity> deleted = this.deleteMissingAssets(owner, desiredAssetIds);
        this.disableEmptyEnvironments(owner, desired.keySet(), deleted);

        return new Result(created, updated, deleted.size());
    }

    private void reconcileEnvironment(String id, Environment environment)
    {
        Optional<EnvironmentEntity> existing = this.repository.findByIdOptional(id);
        if (existing.isEmpty())
        {
            EnvironmentEntity entity = new EnvironmentEntity();
            entity.id = id;
            entity.name = environment.id();
            this.repository.persist(entity);
            logger.debug("Created environment {}", id);
            return;
        }
        EnvironmentEntity entity = existing.get();
        if (entity.disabledAt != null)
        {
            entity.disabledAt = null;
            logger.debug("Re-enabled environment {}", id);
        }
        if (!entity.name.equals(environment.id()))
        {
            entity.name = environment.id();
            logger.debug("Updated environment {} id", id);
        }
    }

    private AssetConfigAction reconcileAsset(String owner, String environmentId, Asset properties)
    {
        Optional<AssetEntity> existing = this.assetRepository.findByIdOptional(properties.identity().id());

        if (existing.isEmpty())
        {
            AssetEntity entity = AssetEntity.from(environmentId, properties);
            entity.owner = owner;
            this.assetRepository.persist(entity);
            this.recordHistory(AssetConfigAction.CREATE, entity);
            logger.debug("Created asset {} in environment {}", properties.identity().id(), environmentId);
            return AssetConfigAction.CREATE;
        }

        AssetEntity entity = existing.get();
        if (entity.deletedAt != null)
        {
            entity.deletedAt = null;
            entity.owner = owner;
            entity.environmentId = environmentId;
            entity.name = properties.identity().name();
            entity.type = properties.type().name();
            entity.properties = properties;
            this.recordHistory(AssetConfigAction.CREATE, entity);
            logger.debug("Re-created (undeleted) asset {} in environment {}", properties.identity().id(), environmentId);
            return AssetConfigAction.CREATE;
        }

        boolean takenOver = !Objects.equals(entity.owner, owner);
        if (takenOver)
        {
            if (!Objects.equals(owner, LOCAL_OWNER))
                throw new EnvironmentConflictException("Asset " + entity.id + " is already declared by " + describe(entity.owner));
            logger.warn("Asset {} declared by {} is now declared by {}", entity.id, describe(entity.owner), describe(owner));
            entity.owner = owner;
        }

        boolean changed = !entity.environmentId.equals(environmentId)
            || !entity.name.equals(properties.identity().name())
            || !Objects.equals(entity.type, properties.type().name())
            || !entity.properties.equals(properties);
        if (changed)
        {
            entity.environmentId = environmentId;
            entity.name = properties.identity().name();
            entity.type = properties.type().name();
            entity.properties = properties;
            this.recordHistory(AssetConfigAction.UPDATE, entity);
            logger.debug("Updated asset {} in environment {}", properties.identity().id(), environmentId);
            return AssetConfigAction.UPDATE;
        }
        return takenOver ? AssetConfigAction.UPDATE : null;
    }

    private List<AssetEntity> deleteMissingAssets(String owner, Set<String> desiredAssetIds)
    {
        List<AssetEntity> deleted = new ArrayList<>();
        for (AssetEntity entity : this.assetRepository.list("deletedAt is null"))
        {
            if (desiredAssetIds.contains(entity.id) || !Objects.equals(entity.owner, owner))
                continue;
            entity.deletedAt = EnvironmentEntity.now();
            this.recordHistory(AssetConfigAction.DELETE, entity);
            deleted.add(entity);
            logger.debug("Soft-deleted asset {} (no longer declared by {})", entity.id, describe(owner));
        }
        return deleted;
    }

    private void disableEmptyEnvironments(String owner, Set<String> desiredEnvironmentIds, Collection<AssetEntity> deleted)
    {
        Set<String> declared = new HashSet<>(desiredEnvironmentIds);
        if (!Objects.equals(owner, LOCAL_OWNER))
            declared.addAll(this.monitoredEnvironments.environments().keySet());

        Set<String> withAssets = new HashSet<>();
        for (AssetEntity entity : this.assetRepository.list("deletedAt is null"))
        {
            if (entity.deletedAt == null)
                withAssets.add(entity.environmentId);
        }

        Set<String> candidates = deleted.stream().map(entity -> entity.environmentId).collect(Collectors.toSet());
        for (EnvironmentEntity entity : this.repository.findActive())
        {
            if (declared.contains(entity.id) || withAssets.contains(entity.id))
                continue;
            if (!Objects.equals(owner, LOCAL_OWNER) && !candidates.contains(entity.id))
                continue;
            entity.disabledAt = EnvironmentEntity.now();
            logger.debug("Disabled environment {} (no longer declared, and without assets)", entity.id);
        }
    }

    private static String describe(String owner)
    {
        return Objects.equals(owner, LOCAL_OWNER) ? "the monitored-environments file" : "worker " + owner;
    }

    private void recordHistory(AssetConfigAction action, AssetEntity entity)
    {
        this.historyRepository.persist(AssetConfigHistory.of(action, entity, snapshot(entity)));
    }

    private static String snapshot(AssetEntity entity)
    {
        try {
            return MAPPER.writeValueAsString(new AssetSnapshot(entity.id, entity.name, entity.type, entity.properties));
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize asset config snapshot for " + entity.id, e);
        }
    }

    public record Result(
        int created,
        int updated,
        int deleted
    ) {}

    private record AssetSnapshot(
        String id,
        String name,
        String type,
        Asset data
    ) {}
}
