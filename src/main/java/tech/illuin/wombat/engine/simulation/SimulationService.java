package tech.illuin.wombat.engine.simulation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import tech.illuin.wombat.engine.context.SlugHelper;
import tech.illuin.wombat.engine.context.asset.UnknownAssetException;
import tech.illuin.wombat.engine.context.asset.persistence.AssetEntity;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.context.environment.UnknownEnvironmentException;
import tech.illuin.wombat.engine.context.environment.persistence.EnvironmentRepository;
import tech.illuin.wombat.engine.simulation.persistence.SimulationAssetEntity;
import tech.illuin.wombat.engine.simulation.persistence.SimulationAssetRepository;
import tech.illuin.wombat.engine.simulation.persistence.SimulationEntity;
import tech.illuin.wombat.engine.simulation.persistence.SimulationRepository;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the simulations: sandboxes of asset definitions that nothing monitors, shared by every user and hard-deleted.
 * A simulation may be generated from an environment, whose picked assets it then copies.
 */
@ApplicationScoped
public class SimulationService
{
    private final SimulationRepository repository;
    private final SimulationAssetRepository simulationAssetRepository;
    private final SimulationAssetService simulationAssets;
    private final EnvironmentRepository environmentRepository;
    private final AssetRepository assetRepository;

    public SimulationService(
        SimulationRepository repository,
        SimulationAssetRepository simulationAssetRepository,
        SimulationAssetService simulationAssets,
        EnvironmentRepository environmentRepository,
        AssetRepository assetRepository
    ) {
        this.repository = repository;
        this.simulationAssetRepository = simulationAssetRepository;
        this.simulationAssets = simulationAssets;
        this.environmentRepository = environmentRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional
    public List<SimulationEntity> list()
    {
        return this.repository.findAllByName();
    }

    @Transactional
    public SimulationEntity get(String id)
    {
        return this.repository.findByIdOptional(id).orElseThrow(() -> new UnknownSimulationException(id));
    }

    /**
     * @return the number of assets per simulation id; simulations without any are absent
     */
    @Transactional
    public Map<String, Long> assetCounts()
    {
        return this.simulationAssetRepository.countBySimulation();
    }

    /**
     * @return the names of the environments simulations may name as their source, by id; deleted ones are absent
     */
    @Transactional
    public Map<String, String> environmentNames()
    {
        return this.environmentRepository.findVisible().stream()
            .collect(Collectors.toMap(environment -> environment.id, environment -> environment.name));
    }

    /**
     * @return the name of the simulation's source environment, empty without one or once it was deleted
     */
    @Transactional
    public Optional<String> sourceEnvironmentName(SimulationEntity simulation)
    {
        if (simulation.sourceEnvironmentId == null)
            return Optional.empty();
        return this.environmentRepository.findVisibleById(simulation.sourceEnvironmentId).map(environment -> environment.name);
    }

    /**
     * Copies the {@code assetIds} of {@code sourceEnvironmentId}, if given, in the same transaction: either they all are,
     * or the simulation is not created.
     *
     * @param referenceWindow {@link SimulationPeriod#MONTH} when null
     * @param assetIds        assets of {@code sourceEnvironmentId}, which they require
     */
    @Transactional
    public SimulationEntity create(
        String id,
        String name,
        String description,
        String notes,
        SimulationPeriod referenceWindow,
        String sourceEnvironmentId,
        List<String> assetIds
    ) {
        if (sourceEnvironmentId == null && !assetIds.isEmpty())
            throw new IllegalArgumentException("assets are copied from a source environment, which is missing");
        if (this.repository.findByIdOptional(id).isPresent())
            throw new SimulationConflictException("Simulation " + id + " already exists");
        if (sourceEnvironmentId != null)
            this.environmentRepository.findVisibleById(sourceEnvironmentId).orElseThrow(() -> new UnknownEnvironmentException(sourceEnvironmentId));

        SimulationEntity entity = new SimulationEntity();
        entity.id = id;
        entity.name = name;
        entity.description = description;
        entity.notes = notes;
        entity.referenceWindow = referenceWindow != null ? referenceWindow : SimulationPeriod.MONTH;
        entity.sourceEnvironmentId = sourceEnvironmentId;
        this.repository.persist(entity);

        Set<String> taken = new HashSet<>();
        for (String assetId : new LinkedHashSet<>(assetIds))
        {
            AssetEntity source = this.assetRepository.findVisibleById(assetId)
                .filter(asset -> asset.environmentId.equals(sourceEnvironmentId))
                .orElseThrow(() -> new UnknownAssetException(assetId));
            String copyId = SimulationAssetService.uniqueId(SlugHelper.slugify(source.id), taken);
            taken.add(copyId);
            this.simulationAssetRepository.persist(this.simulationAssets.copy(entity, source, copyId));
        }
        return entity;
    }

    /**
     * Replaces the editable fields; the id and the source environment are immutable.
     */
    @Transactional
    public SimulationEntity update(String id, String name, String description, String notes, SimulationPeriod referenceWindow)
    {
        SimulationEntity entity = this.get(id);
        entity.name = name;
        entity.description = description;
        entity.notes = notes;
        entity.referenceWindow = referenceWindow;
        return entity;
    }

    /**
     * Copies the simulation with all its assets, which keep their ids, sources and sync times.
     */
    @Transactional
    public SimulationEntity duplicate(String originalId, String id, String name)
    {
        SimulationEntity original = this.get(originalId);
        if (this.repository.findByIdOptional(id).isPresent())
            throw new SimulationConflictException("Simulation " + id + " already exists");

        SimulationEntity entity = new SimulationEntity();
        entity.id = id;
        entity.name = name;
        entity.description = original.description;
        entity.notes = original.notes;
        entity.referenceWindow = original.referenceWindow;
        entity.sourceEnvironmentId = original.sourceEnvironmentId;
        this.repository.persist(entity);

        for (SimulationAssetEntity asset : this.simulationAssetRepository.findBySimulation(originalId))
            this.simulationAssetRepository.persist(this.simulationAssets.duplicate(entity, asset));
        return entity;
    }

    /**
     * Hard-deletes the simulation and its assets.
     */
    @Transactional
    public void delete(String id)
    {
        SimulationEntity entity = this.get(id);
        this.simulationAssetRepository.deleteBySimulation(id);
        this.repository.delete(entity);
    }
}
