package tech.illuin.wombat.context;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import tech.illuin.wombat.context.persistence.AssetConfigAction;
import tech.illuin.wombat.context.persistence.AssetConfigHistory;
import tech.illuin.wombat.context.persistence.AssetConfigHistoryRepository;
import tech.illuin.wombat.context.persistence.AssetEntity;
import tech.illuin.wombat.context.persistence.AssetRepository;
import tech.illuin.wombat.context.persistence.EnvironmentEntity;
import tech.illuin.wombat.context.persistence.EnvironmentRepository;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetType;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.monitor.MonitoredEnvironments;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Makes the persisted environments/assets match the monitored-environments YAML exactly at every
 * startup: the YAML is the source of truth, the DB its persisted, historized projection. Config
 * lines present only in the DB are soft-deleted, new lines are created, and changed lines are
 * updated. Every asset change appends a row to {@link AssetConfigHistory}.
 */
@ApplicationScoped
public class EnvironmentReconciler
{
    static final int STARTUP_PRIORITY_RECONCILE = 2000;

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
    )
    {
        this.repository = repository;
        this.assetRepository = assetRepository;
        this.historyRepository = historyRepository;
        this.monitoredEnvironments = monitoredEnvironments;
    }

    @Transactional
    void onStart(@Observes @Priority(STARTUP_PRIORITY_RECONCILE) StartupEvent event)
    {
        Map<String, Environment> desired = this.monitoredEnvironments.environments();
        int created = 0;
        int updated = 0;
        int deleted = 0;

        Set<String> desiredEnvironmentIds = desired.keySet();
        Set<String> desiredAssetIds = new HashSet<>();

        for (Map.Entry<String, Environment> entry : desired.entrySet())
        {
            this.reconcileEnvironment(entry.getKey(), entry.getValue());
            for (Asset asset : entry.getValue().assets())
            {
                desiredAssetIds.add(asset.id());
                AssetConfigAction action = this.reconcileAsset(entry.getKey(), asset);
                if (action == AssetConfigAction.CREATE)
                    created++;
                else if (action == AssetConfigAction.UPDATE)
                    updated++;
            }
        }

        deleted += this.deleteMissingAssets(desiredAssetIds);
        this.disableMissingEnvironments(desiredEnvironmentIds);

        logger.info("Environment reconciliation complete: {} created, {} updated, {} deleted assets", created, updated, deleted);
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

    private AssetConfigAction reconcileAsset(String environmentId, Asset properties)
    {
        Optional<AssetEntity> existing = this.assetRepository.findByIdOptional(properties.id());

        if (existing.isEmpty())
        {
            AssetEntity entity = AssetEntity.from(environmentId, properties);
            this.assetRepository.persist(entity);
            this.recordHistory(AssetConfigAction.CREATE, entity);
            logger.debug("Created asset {} in environment {}", properties.id(), environmentId);
            return AssetConfigAction.CREATE;
        }

        AssetEntity entity = existing.get();
        if (entity.deletedAt != null)
        {
            entity.deletedAt = null;
            entity.environmentId = environmentId;
            entity.name = properties.name();
            entity.type = properties.type();
            entity.properties = properties;
            this.recordHistory(AssetConfigAction.CREATE, entity);
            logger.debug("Re-created (undeleted) asset {} in environment {}", properties.id(), environmentId);
            return AssetConfigAction.CREATE;
        }

        boolean changed = !entity.environmentId.equals(environmentId)
            || !entity.name.equals(properties.name())
            || entity.type != properties.type()
            || !entity.properties.equals(properties);
        if (changed)
        {
            entity.environmentId = environmentId;
            entity.name = properties.name();
            entity.type = properties.type();
            entity.properties = properties;
            this.recordHistory(AssetConfigAction.UPDATE, entity);
            logger.debug("Updated asset {} in environment {}", properties.id(), environmentId);
            return AssetConfigAction.UPDATE;
        }
        return null;
    }

    private int deleteMissingAssets(Set<String> desiredAssetIds)
    {
        int deleted = 0;
        for (AssetEntity entity : this.assetRepository.list("deletedAt is null"))
        {
            if (desiredAssetIds.contains(entity.id))
                continue;
            entity.deletedAt = EnvironmentEntity.now();
            this.recordHistory(AssetConfigAction.DELETE, entity);
            deleted++;
            logger.debug("Soft-deleted asset {} (absent from YAML)", entity.id);
        }
        return deleted;
    }

    private void disableMissingEnvironments(Set<String> desiredEnvironmentIds)
    {
        for (EnvironmentEntity entity : this.repository.findActive())
        {
            if (desiredEnvironmentIds.contains(entity.id))
                continue;
            entity.disabledAt = EnvironmentEntity.now();
            logger.debug("Disabled environment {} (absent from YAML)", entity.id);
        }
    }

    private void recordHistory(AssetConfigAction action, AssetEntity entity)
    {
        this.historyRepository.persist(AssetConfigHistory.of(action, entity, snapshot(entity)));
    }

    private static String snapshot(AssetEntity entity)
    {
        try
        {
            return MAPPER.writeValueAsString(new AssetSnapshot(entity.id, entity.name, entity.type, entity.properties));
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize asset config snapshot for " + entity.id, e);
        }
    }

    private record AssetSnapshot(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("type") AssetType type,
        @JsonProperty("data") Asset data
    ) {}
}
