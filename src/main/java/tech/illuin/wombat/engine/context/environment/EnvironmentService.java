package tech.illuin.wombat.engine.context.environment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentEntity;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Manages the environments created through the API. Provisioned ones (declared by the monitored-environments file or a
 * worker) are read-only here, see {@link EnvironmentReconciler}; soft-deleted ones are unknown.
 */
@ApplicationScoped
public class EnvironmentService
{
    private final EnvironmentRepository repository;
    private final AssetRepository assetRepository;

    public EnvironmentService(EnvironmentRepository repository, AssetRepository assetRepository)
    {
        this.repository = repository;
        this.assetRepository = assetRepository;
    }

    @Transactional
    public List<EnvironmentEntity> list()
    {
        return this.repository.findVisible();
    }

    @Transactional
    public EnvironmentEntity get(String id)
    {
        return this.repository.findVisibleById(id).orElseThrow(() -> new UnknownEnvironmentException(id));
    }

    @Transactional
    public EnvironmentEntity getByUuid(String uuid)
    {
        return this.repository.findVisibleByUuid(uuid).orElseThrow(() -> new UnknownEnvironmentException(uuid));
    }

    /**
     * Takes over a soft-deleted environment with the same id, which then keeps its uuid.
     */
    @Transactional
    public EnvironmentEntity create(String id, String name, String description, boolean disabled)
    {
        Optional<EnvironmentEntity> existing = this.repository.findByIdOptional(id);
        if (existing.isPresent() && existing.get().deletedAt == null)
            throw new EnvironmentConflictException("Environment " + id + " already exists");

        EnvironmentEntity entity = existing.orElseGet(EnvironmentEntity::new);
        entity.id = id;
        entity.name = name;
        entity.description = description;
        entity.provisioned = false;
        entity.deletedAt = null;
        entity.disabledAt = disabled ? EnvironmentEntity.now() : null;
        if (existing.isEmpty())
            this.repository.persist(entity);
        return entity;
    }

    @Transactional
    public EnvironmentEntity update(String id, String name, String description, boolean disabled)
    {
        EnvironmentEntity entity = this.editable(id);
        entity.name = name;
        entity.description = description;
        entity.disabledAt = disabledAt(entity.disabledAt, disabled);
        return entity;
    }

    /**
     * Soft-deletes an empty environment: its assets have to be deleted first.
     */
    @Transactional
    public void delete(String id)
    {
        EnvironmentEntity entity = this.editable(id);
        if (this.assetRepository.countVisibleByEnvironment(id) > 0)
            throw new EnvironmentConflictException("Environment " + id + " still holds assets");
        entity.deletedAt = EnvironmentEntity.now();
    }

    private EnvironmentEntity editable(String id)
    {
        EnvironmentEntity entity = this.get(id);
        if (entity.provisioned)
            throw new EnvironmentConflictException("Environment " + id + " is provisioned, and cannot be edited");
        return entity;
    }

    /** Keeps the time it was disabled at while it stays disabled. */
    public static Instant disabledAt(Instant current, boolean disabled)
    {
        if (!disabled)
            return null;
        return current != null ? current : EnvironmentEntity.now();
    }
}
