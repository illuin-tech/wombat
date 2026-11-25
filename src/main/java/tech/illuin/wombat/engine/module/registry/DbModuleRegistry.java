package tech.illuin.wombat.engine.module.registry;

import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.core.asset.type.ModuleType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.engine.module.persistence.CoreInstanceRepository;
import tech.illuin.wombat.engine.module.persistence.ModuleEntity;
import tech.illuin.wombat.engine.module.persistence.ModuleRepository;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata.Feature;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata.Kind;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata.Source;

import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The modules registered in the {@code modules} table, shared by every instance: a toggle is seen by all of them at
 * once. Every module this instance detects is registered at startup; a registered module no live instance detects any
 * more (see the heartbeats of {@link CoreInstanceRepository}) is missing, and keeps its state should it come back.
 */
@ApplicationScoped
public class DbModuleRegistry implements ModuleRegistry
{
    private final Map<String, WombatModule> detected;
    private final ModuleRepository repository;
    private final CoreInstanceRepository instances;
    private final Path extensionPath;
    private final Duration pollInterval;
    private volatile Set<String> newlyRegistered;

    /* After the persistence setup, and before the environments are reconciled and the core deployed */
    static final int STARTUP_PRIORITY_REGISTER = 1900;
    private static final Logger logger = LoggerFactory.getLogger(DbModuleRegistry.class);

    @Inject
    public DbModuleRegistry(List<WombatModule> modules, ModuleRepository repository, CoreInstanceRepository instances, WombatProperties wombat)
    {
        this(modules, repository, instances, wombat.module().extension().path(), wombat.module().deployment().pollInterval());
    }

    DbModuleRegistry(List<WombatModule> modules, ModuleRepository repository, CoreInstanceRepository instances, Path extensionPath, Duration pollInterval)
    {
        /* In detection order, which the modules are listed and registered in */
        this.detected = modules.stream().collect(Collectors.toMap(
            module -> module.type().name(),
            Function.identity(),
            (first, second) -> {
                throw new IllegalStateException("Duplicate module " + first.type().name());
            },
            LinkedHashMap::new
        ));
        this.repository = repository;
        this.instances = instances;
        this.extensionPath = extensionPath.toAbsolutePath().normalize();
        this.pollInterval = pollInterval;
        this.newlyRegistered = Set.of();
    }

    /**
     * Registers the detected modules that have no row yet, enabled; the existing rows keep their state.
     */
    void onStart(@Observes @Priority(STARTUP_PRIORITY_REGISTER) StartupEvent event)
    {
        List<ModuleType> types = this.detected.values().stream()
            .map(WombatModule::type)
            .toList();
        try {
            Set<String> registered = this.repository.register(types);
            if (!registered.isEmpty())
                logger.info("Registered {} new module(s): {}", registered.size(), registered);
            this.newlyRegistered = Set.copyOf(registered);
        }
        catch (PersistenceException e) {
            /* Most likely another instance registering the same modules at once: a module with no row is enabled anyway */
            logger.warn("Could not register the detected modules, they are enabled until registered: {}", e.getMessage(), e);
        }
    }

    @Override
    public Set<String> newlyRegistered()
    {
        return this.newlyRegistered;
    }

    /**
     * @return the modules detected here, then those registered but not detected here
     */
    @Override
    @Transactional
    public List<ModuleMetadata> listModules()
    {
        Map<String, ModuleEntity> rows = this.repository.listAll().stream()
            .collect(Collectors.toMap(entity -> entity.typeName, Function.identity()));
        Set<String> detectedElsewhere = this.detectedByLiveInstances();

        Stream<ModuleMetadata> detected = this.detected.values().stream()
            .map(module -> this.detected(module, rows.get(module.type().name())));
        Stream<ModuleMetadata> others = rows.values().stream()
            .filter(entity -> !this.detected.containsKey(entity.typeName))
            .map(entity -> notDetectedHere(entity, !detectedElsewhere.contains(entity.typeName)));
        return Stream.concat(detected, others).toList();
    }

    @Override
    @Transactional
    public Optional<ModuleMetadata> getModule(String module)
    {
        ModuleEntity entity = this.repository.findById(module);
        WombatModule detected = this.detected.get(module);
        if (detected != null)
            return Optional.of(this.detected(detected, entity));
        return Optional.ofNullable(entity).map(this::notDetectedHere);
    }

    @Override
    @Transactional
    public ModuleMetadata setEnabled(String module, boolean enabled, String user)
    {
        ModuleEntity entity = this.repository.findById(module);
        WombatModule detected = this.detected.get(module);
        if (detected == null && entity == null)
            throw new UnknownModuleException(module);
        if (detected == null && this.notDetectedHere(entity).missing())
            throw new ModuleConflictException("Module " + module + " is not detected, it cannot be toggled");

        /* Registered at startup, unless that failed */
        if (entity == null)
        {
            entity = ModuleEntity.registered(detected.type());
            this.repository.persist(entity);
        }
        entity.enabled = enabled;
        entity.updatedBy = user;
        return detected != null ? this.detected(detected, entity) : this.notDetectedHere(entity);
    }

    @Override
    @Transactional
    public void setFamilyOrder(ServiceFamily family, List<String> modules, String user)
    {
        List<String> members = this.listModules().stream()
            .filter(module -> module.kind() == Kind.family && module.type().family() == family)
            .map(module -> module.type().name())
            .toList();
        ModuleOrderException.check(family, members, modules);

        for (int index = 0; index < modules.size(); index++)
        {
            String name = modules.get(index);
            ModuleEntity entity = this.repository.findById(name);
            /* Registered at startup, unless that failed */
            if (entity == null)
            {
                entity = ModuleEntity.registered(this.detected.get(name).type());
                this.repository.persist(entity);
            }
            if (!Objects.equals(entity.ordering, index + 1))
            {
                entity.ordering = index + 1;
                entity.updatedBy = user;
            }
        }
    }

    @Override
    @Transactional
    public void remove(String module)
    {
        ModuleEntity entity = this.repository.findByIdOptional(module)
            .orElseThrow(() -> new UnknownModuleException(module));
        if (this.detected.containsKey(module) || !this.notDetectedHere(entity).missing())
            throw new ModuleConflictException("Module " + module + " is detected, only a missing module can be removed");

        this.repository.delete(entity);
    }

    private ModuleMetadata detected(WombatModule module, ModuleEntity entity)
    {
        return new ModuleMetadata(
            module.type(),
            module.assetClass().orElse(null),
            entity == null || entity.enabled,
            false,
            this.isExtension(module.getClass()) ? Source.extension : Source.core,
            entity == null ? null : entity.ordering,
            Feature.of(module)
        );
    }

    private ModuleMetadata notDetectedHere(ModuleEntity entity)
    {
        return notDetectedHere(entity, !this.detectedByLiveInstances().contains(entity.typeName));
    }

    private static ModuleMetadata notDetectedHere(ModuleEntity entity, boolean missing)
    {
        return new ModuleMetadata(entity.type(), null, entity.enabled, missing, null, entity.ordering, Set.of());
    }

    private Set<String> detectedByLiveInstances()
    {
        return this.instances.findLive(this.pollInterval).stream()
            .flatMap(instance -> instance.detectedModules.stream())
            .collect(Collectors.toSet());
    }

    /**
     * @return whether the class was loaded from a jar of the extension folder
     */
    private boolean isExtension(Class<?> type)
    {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null)
            return false;
        try {
            Path location = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
            return this.extensionPath.equals(location.getParent());
        }
        catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException e) {
            /* Not a file, so not a jar of the extension folder */
            return false;
        }
    }
}
