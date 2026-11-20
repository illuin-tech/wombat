package tech.illuin.wombat.engine.simulation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.SimulatedAsset;
import tech.illuin.wombat.engine.context.SlugHelper;
import tech.illuin.wombat.engine.context.asset.AssetDefinitionReader;
import tech.illuin.wombat.engine.context.asset.InvalidAssetException;
import tech.illuin.wombat.engine.context.asset.UnknownAssetException;
import tech.illuin.wombat.engine.context.asset.model.UnrecognizedAsset;
import tech.illuin.wombat.engine.context.asset.persistence.AssetEntity;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentEntity;
import tech.illuin.wombat.engine.simulation.model.ParentStatus;
import tech.illuin.wombat.engine.simulation.model.SimulationAssetInfo;
import tech.illuin.wombat.engine.simulation.persistence.SimulationAssetEntity;
import tech.illuin.wombat.engine.simulation.persistence.SimulationAssetRepository;
import tech.illuin.wombat.engine.simulation.persistence.SimulationEntity;
import tech.illuin.wombat.engine.simulation.persistence.SimulationRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages the asset definitions of the simulations: copies of real assets, kept as snapshots and synced on demand, or
 * definitions written from scratch. Each is stored under the identity of the simulation asset (see
 * {@link #identity(String, String, String)}), never the parent's.
 */
@ApplicationScoped
public class SimulationAssetService
{
    private final SimulationAssetRepository repository;
    private final SimulationRepository simulationRepository;
    private final AssetRepository assetRepository;
    private final AssetDefinitionReader reader;
    private final JsonMapper mapper;

    private static final List<String> IDENTITY_FIELDS = List.of("id", "environmentId", "environment-id", "type");

    public SimulationAssetService(
        SimulationAssetRepository repository,
        SimulationRepository simulationRepository,
        AssetRepository assetRepository,
        AssetDefinitionReader reader,
        JsonMapper mapper
    ) {
        this.repository = repository;
        this.simulationRepository = simulationRepository;
        this.assetRepository = assetRepository;
        this.reader = reader;
        this.mapper = mapper;
    }

    /**
     * The identity a simulation asset's definition is stored under, apart from any real asset's.
     */
    public static AssetIdentity identity(String simulationId, String id, String type)
    {
        return AssetIdentity.of(id, "simulation:" + simulationId, type);
    }

    @Transactional
    public List<SimulationAssetEntity> list(String simulationId)
    {
        this.simulation(simulationId);
        return this.repository.findBySimulation(simulationId);
    }

    @Transactional
    public SimulationAssetEntity get(String simulationId, String id)
    {
        this.simulation(simulationId);
        return this.repository.findOne(simulationId, id).orElseThrow(() -> new UnknownSimulationAssetException(simulationId, id));
    }

    /**
     * Copies {@code parentAssetId} when given: its type, and its properties unless {@code properties} are given, e.g. a
     * copy edited before it was first saved. Without a parent, {@code id}, {@code type} and {@code properties} are
     * required. The id of a copy defaults to its parent's, made valid and unique within the simulation.
     */
    @Transactional
    public SimulationAssetEntity create(
        String simulationId,
        String id,
        String name,
        String description,
        String notes,
        String parentAssetId,
        String type,
        ObjectNode properties
    ) {
        SimulationEntity simulation = this.simulation(simulationId);
        if (parentAssetId == null)
        {
            if (id == null || type == null || properties == null)
                throw new InvalidAssetException("An asset without parent requires an id, a type and properties");
            if (this.repository.findOne(simulationId, id).isPresent())
                throw new SimulationAssetConflictException("Asset " + id + " already exists in simulation " + simulationId);
            SimulationAssetEntity entity = this.newEntity(simulation, id, name, description, notes);
            entity.type = type;
            entity.properties = this.readValid(simulationId, id, type, properties);
            this.repository.persist(entity);
            return entity;
        }

        AssetEntity parent = this.assetRepository.findVisibleById(parentAssetId).orElseThrow(() -> new UnknownAssetException(parentAssetId));
        if (type != null && !type.equals(parent.type))
            throw new InvalidAssetException("Asset " + parentAssetId + " is of type " + parent.type + ", not " + type);
        if (id != null && this.repository.findOne(simulationId, id).isPresent())
            throw new SimulationAssetConflictException("Asset " + id + " already exists in simulation " + simulationId);

        String copyId = id != null ? id : this.uniqueId(simulationId, parent.id);
        SimulationAssetEntity entity = this.copy(simulation, parent, copyId, properties != null ? properties : this.definition(parent.properties));
        entity.name = name;
        entity.description = description;
        entity.notes = notes;
        this.repository.persist(entity);
        return entity;
    }

    @Transactional
    public SimulationAssetEntity update(String simulationId, String id, String name, String description, String notes, ObjectNode properties)
    {
        SimulationAssetEntity entity = this.get(simulationId, id);
        entity.name = name;
        entity.description = description;
        entity.notes = notes;
        entity.properties = this.readValid(simulationId, id, entity.type, properties);
        return entity;
    }

    @Transactional
    public void delete(String simulationId, String id)
    {
        this.repository.delete(this.get(simulationId, id));
    }

    /**
     * Replaces the definition with the parent's current one, overwriting any change made to the copy.
     */
    @Transactional
    public SimulationAssetEntity sync(String simulationId, String id)
    {
        SimulationAssetEntity entity = this.get(simulationId, id);
        if (entity.parentAssetId == null)
            throw new SimulationAssetConflictException("Asset " + id + " was not copied from another one");

        AssetEntity parent = this.assetRepository.findVisibleById(entity.parentAssetId)
            .filter(candidate -> this.isParent(entity, candidate))
            .orElseThrow(() -> new SimulationAssetConflictException("The asset " + id + " was copied from was deleted"));
        entity.properties = this.rebind(parent.properties, identity(simulationId, id, entity.type));
        entity.syncedAt = EnvironmentEntity.now();
        return entity;
    }

    /**
     * Describes the assets, with how each copy stands against its parent; the parents are read at once.
     */
    @Transactional
    public List<SimulationAssetInfo> describe(Collection<SimulationAssetEntity> entities)
    {
        Set<String> parentIds = entities.stream()
            .map(entity -> entity.parentAssetId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<String, AssetEntity> parents = this.assetRepository.findVisibleByIds(parentIds).stream()
            .collect(Collectors.toMap(parent -> parent.id, Function.identity()));

        return entities.stream()
            .map(entity -> SimulationAssetInfo.from(entity, this.parentStatus(entity, parents.get(entity.parentAssetId))))
            .toList();
    }

    /**
     * A copy of {@code parent} into {@code simulation}, not persisted yet.
     */
    SimulationAssetEntity copy(SimulationEntity simulation, AssetEntity parent, String id)
    {
        return this.copy(simulation, parent, id, this.definition(parent.properties));
    }

    /* Validated like any definition written through the API: the parent's type may have lost its module since */
    private SimulationAssetEntity copy(SimulationEntity simulation, AssetEntity parent, String id, ObjectNode definition)
    {
        SimulationAssetEntity entity = this.newEntity(simulation, id, parent.name, parent.description, null);
        entity.parentAssetId = parent.id;
        entity.parentEnvironmentId = parent.environmentId;
        entity.type = parent.type;
        entity.properties = this.readValid(simulation.id, id, parent.type, definition);
        entity.syncedAt = EnvironmentEntity.now();
        return entity;
    }

    /**
     * A copy of {@code source} into {@code simulation} under the same id, keeping its parent and when it was synced,
     * not persisted yet.
     */
    SimulationAssetEntity duplicate(SimulationEntity simulation, SimulationAssetEntity source)
    {
        SimulationAssetEntity entity = this.newEntity(simulation, source.id, source.name, source.description, source.notes);
        entity.parentAssetId = source.parentAssetId;
        entity.parentEnvironmentId = source.parentEnvironmentId;
        entity.type = source.type;
        entity.properties = this.rebind(source.properties, identity(simulation.id, source.id, source.type));
        entity.syncedAt = source.syncedAt;
        return entity;
    }

    /**
     * {@code base} made a valid id, suffixed with {@code -2}, {@code -3}… until no asset of the simulation holds it.
     */
    String uniqueId(String simulationId, String base)
    {
        Set<String> taken = this.repository.findBySimulation(simulationId).stream()
            .map(entity -> entity.id)
            .collect(Collectors.toSet());
        return uniqueId(SlugHelper.slugify(base), taken);
    }

    static String uniqueId(String slug, Set<String> taken)
    {
        String candidate = slug;
        for (int n = 2; taken.contains(candidate); n++)
        {
            String suffix = "-" + n;
            candidate = slug.substring(0, Math.min(slug.length(), SlugHelper.MAX_LENGTH - suffix.length())) + suffix;
        }
        return candidate;
    }

    private SimulationEntity simulation(String simulationId)
    {
        return this.simulationRepository.findByIdOptional(simulationId).orElseThrow(() -> new UnknownSimulationException(simulationId));
    }

    private SimulationAssetEntity newEntity(SimulationEntity simulation, String id, String name, String description, String notes)
    {
        SimulationAssetEntity entity = new SimulationAssetEntity();
        entity.simulationId = simulation.id;
        entity.id = id;
        entity.name = name;
        entity.description = description;
        entity.notes = notes;
        return entity;
    }

    private Asset readValid(String simulationId, String id, String type, ObjectNode properties)
    {
        Asset asset = this.reader.readValid(identity(simulationId, id, type), properties);
        if (asset instanceof SimulatedAsset)
            throw new InvalidAssetException("Asset type " + type + " only serves stateless simulations, and cannot be held by one");
        return asset;
    }

    private Asset rebind(Asset asset, AssetIdentity identity)
    {
        return this.reader.read(identity, this.definition(asset));
    }

    /* A parent deleted then taken over in another environment is another asset */
    private boolean isParent(SimulationAssetEntity entity, AssetEntity candidate)
    {
        return candidate.environmentId.equals(entity.parentEnvironmentId);
    }

    private ParentStatus parentStatus(SimulationAssetEntity entity, AssetEntity parent)
    {
        if (entity.parentAssetId == null)
            return null;
        if (parent == null || !this.isParent(entity, parent))
            return ParentStatus.PARENT_REMOVED;
        return this.definition(entity.properties).equals(this.definition(parent.properties))
            ? ParentStatus.IN_SYNC
            : ParentStatus.DIVERGED;
    }

    /* The definition without its identity, so that a copy and its parent compare equal; an unrecognized asset only
       keeps it as raw JSON */
    private ObjectNode definition(Asset asset)
    {
        try {
            ObjectNode node = asset instanceof UnrecognizedAsset unrecognized && unrecognized.rawJson() != null
                ? (ObjectNode) this.mapper.readTree(unrecognized.rawJson())
                : this.mapper.valueToTree(asset);
            node.remove(IDENTITY_FIELDS);
            return node;
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to read the stored definition of asset " + asset.identity().id(), e);
        }
    }
}
