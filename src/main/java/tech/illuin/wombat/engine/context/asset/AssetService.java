package tech.illuin.wombat.engine.context.asset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.engine.context.asset.persistence.AssetConfigAction;
import tech.illuin.wombat.engine.context.asset.persistence.AssetEntity;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.environment.EnvironmentService;
import tech.illuin.wombat.engine.context.environment.UnknownEnvironmentException;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentEntity;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentRepository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Manages the assets created through the API. Provisioned ones (declared by the monitored-environments file or a
 * worker) are read-only here, see {@link EnvironmentReconciler}; soft-deleted ones are unknown. Every change is
 * historized, like those of the reconciler.
 */
@ApplicationScoped
public class AssetService
{
    private final AssetRepository repository;
    private final EnvironmentRepository environmentRepository;
    private final AssetHistoryRecorder history;
    private final AssetValidator assetValidator;
    private final Validator validator;
    private final JsonMapper assetMapper;
    private final AssetMonitor monitor;
    private final AssetEvaluator evaluator;

    private static final Duration REFRESH_DELAY = Duration.ofSeconds(30);

    public AssetService(
        AssetRepository repository,
        EnvironmentRepository environmentRepository,
        AssetHistoryRecorder history,
        AssetValidator assetValidator,
        Validator validator,
        JsonMapper assetMapper,
        AssetMonitor monitor,
        AssetEvaluator evaluator
    ) {
        this.repository = repository;
        this.environmentRepository = environmentRepository;
        this.history = history;
        this.assetValidator = assetValidator;
        this.validator = validator;
        this.assetMapper = assetMapper;
        this.monitor = monitor;
        this.evaluator = evaluator;
    }

    @Transactional
    public List<AssetEntity> list(Optional<String> environmentId)
    {
        if (environmentId.isEmpty())
            return this.repository.findVisible();
        this.environment(environmentId.get());
        return this.repository.findVisibleByEnvironment(environmentId.get());
    }

    @Transactional
    public AssetEntity get(String id)
    {
        return this.repository.findVisibleById(id).orElseThrow(() -> new UnknownAssetException(id));
    }

    @Transactional
    public AssetEntity getByUuid(String uuid)
    {
        return this.repository.findVisibleByUuid(uuid).orElseThrow(() -> new UnknownAssetException(uuid));
    }

    /**
     * Takes over a soft-deleted asset with the same id, which then keeps its uuid. Any environment can hold it,
     * provisioned or not. The {@code properties} are the type-specific ones (e.g. its {@code profile}), in JSON.
     */
    @Transactional
    public AssetEntity create(String id, String environmentId, String name, String type, String description, boolean disabled, ObjectNode properties)
    {
        this.environment(environmentId);
        Optional<AssetEntity> existing = this.repository.findByIdOptional(id);
        if (existing.isPresent() && existing.get().deletedAt == null)
            throw new AssetConflictException("Asset " + id + " already exists");

        Asset asset = this.toAsset(id, environmentId, type, properties);

        AssetEntity entity = existing.orElseGet(AssetEntity::new);
        entity.id = id;
        entity.environmentId = environmentId;
        entity.owner = null;
        entity.provisioned = false;
        entity.deletedAt = null;
        entity.description = description;
        entity.disabledAt = disabled ? EnvironmentEntity.now() : null;
        apply(entity, name, asset);
        if (existing.isEmpty())
            this.repository.persist(entity);
        this.history.record(AssetConfigAction.CREATE, entity);
        return entity;
    }

    @Transactional
    public AssetEntity update(String id, String name, String description, boolean disabled, ObjectNode properties)
    {
        AssetEntity entity = this.editable(id);
        Asset asset = this.toAsset(entity.id, entity.environmentId, entity.type, properties);

        boolean changed = !entity.name.equals(name) || !entity.properties.equals(asset);
        entity.description = description;
        entity.disabledAt = EnvironmentService.disabledAt(entity.disabledAt, disabled);
        if (changed)
        {
            apply(entity, name, asset);
            this.history.record(AssetConfigAction.UPDATE, entity);
            this.refreshLater(asset.identity());
        }
        return entity;
    }

    @Transactional
    public void delete(String id)
    {
        AssetEntity entity = this.editable(id);
        entity.deletedAt = EnvironmentEntity.now();
        this.history.record(AssetConfigAction.DELETE, entity);
        this.refreshLater(entity.properties.identity());
    }

    /**
     * Has the monitor and evaluator discard the components they built for the asset, after a delay leaving the change
     * time to be committed: discarded any sooner, a component could be built again from the asset as it was before.
     */
    private void refreshLater(AssetIdentity identity)
    {
        CompletableFuture.runAsync(() -> {
            this.monitor.refresh(identity);
            this.evaluator.refresh(identity);
        }, CompletableFuture.delayedExecutor(REFRESH_DELAY.toSeconds(), TimeUnit.SECONDS));
    }

    private AssetEntity editable(String id)
    {
        AssetEntity entity = this.get(id);
        if (entity.provisioned)
            throw new AssetConflictException("Asset " + id + " is provisioned, and cannot be edited");
        return entity;
    }

    private EnvironmentEntity environment(String id)
    {
        return this.environmentRepository.findVisibleById(id).orElseThrow(() -> new UnknownEnvironmentException(id));
    }

    /**
     * Reads the properties as the asset of the given type, its identity taking precedence over the one they may carry,
     * then checks it as a declared one would be.
     */
    private Asset toAsset(String id, String environmentId, String type, ObjectNode properties)
    {
        ObjectNode node = properties.deepCopy();
        node.put("id", id);
        node.put("environment-id", environmentId);
        node.put("type", type);

        try {
            Asset asset = this.assetMapper.treeToValue(node, Asset.class);

            this.assetValidator.validate(asset);

            Set<ConstraintViolation<Asset>> violations = this.validator.validate(asset);
            if (!violations.isEmpty())
            {
                throw new InvalidAssetException("Invalid properties for asset " + id + ": " + violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", ")));
            }
            return asset;
        }
        catch (JsonProcessingException e) {
            throw new InvalidAssetException("Invalid properties for asset " + id + " of type " + type + ": " + e.getOriginalMessage());
        }
    }

    private static void apply(AssetEntity entity, String name, Asset properties)
    {
        entity.name = name;
        entity.type = properties.type().name();
        entity.properties = properties;
    }
}
