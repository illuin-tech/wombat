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
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.engine.module.persistence.ModuleEntity;
import tech.illuin.wombat.engine.module.persistence.ModuleRepository;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata.Source;

import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The modules registered in the {@code modules} table, shared by every instance: a toggle is seen by all of them at
 * once. Every module this instance detects is registered at startup; a registered module no instance detects any more
 * is missing, and keeps its state should it come back.
 */
@ApplicationScoped
public class DbModuleRegistry implements ModuleRegistry
{
    private final Map<String, WombatModule> detected;
    private final ModuleRepository repository;
    private final Path extensionPath;

    /* After the persistence setup, and before the environments are reconciled and the core deployed */
    static final int STARTUP_PRIORITY_REGISTER = 1900;
    private static final Logger logger = LoggerFactory.getLogger(DbModuleRegistry.class);

    @Inject
    public DbModuleRegistry(List<WombatModule> modules, ModuleRepository repository, WombatProperties wombat)
    {
        this(modules, repository, wombat.module().extension().path());
    }

    DbModuleRegistry(List<WombatModule> modules, ModuleRepository repository, Path extensionPath)
    {
        this.detected = modules.stream().collect(Collectors.toMap(module -> module.type().name(), Function.identity()));
        this.repository = repository;
        this.extensionPath = extensionPath.toAbsolutePath().normalize();
    }

    /**
     * Registers the detected modules that have no row yet, enabled; the existing rows keep their state.
     */
    void onStart(@Observes @Priority(STARTUP_PRIORITY_REGISTER) StartupEvent event)
    {
        List<AssetType> types = this.detected.values().stream()
            .map(WombatModule::type)
            .toList();
        try {
            Set<String> registered = this.repository.register(types);
            if (!registered.isEmpty())
                logger.info("Registered {} new module(s): {}", registered.size(), registered);
        }
        catch (PersistenceException e) {
            /* Most likely another instance registering the same modules at once: a module with no row is enabled anyway */
            logger.warn("Could not register the detected modules, they are enabled until registered: {}", e.getMessage(), e);
        }
    }

    /**
     * @return the detected modules, then the missing ones
     */
    @Override
    @Transactional
    public List<ModuleMetadata> listModules()
    {
        Map<String, ModuleEntity> rows = this.repository.listAll().stream()
            .collect(Collectors.toMap(entity -> entity.typeName, Function.identity()));

        Stream<ModuleMetadata> detected = this.detected.values().stream()
            .map(module -> this.detected(module, rows.get(module.type().name())));
        Stream<ModuleMetadata> missing = rows.values().stream()
            .filter(entity -> !this.detected.containsKey(entity.typeName))
            .map(DbModuleRegistry::missing);
        return Stream.concat(detected, missing).toList();
    }

    @Override
    @Transactional
    public Optional<ModuleMetadata> getModule(String module)
    {
        ModuleEntity entity = this.repository.findById(module);
        WombatModule detected = this.detected.get(module);
        if (detected != null)
            return Optional.of(this.detected(detected, entity));
        return Optional.ofNullable(entity).map(DbModuleRegistry::missing);
    }

    @Override
    @Transactional
    public ModuleMetadata setEnabled(String module, boolean enabled, String user)
    {
        ModuleEntity entity = this.repository.findById(module);
        WombatModule detected = this.detected.get(module);
        if (detected == null)
        {
            if (entity == null)
                throw new UnknownModuleException(module);
            throw new ModuleConflictException("Module " + module + " is not detected, it cannot be toggled");
        }

        /* Registered at startup, unless that failed */
        if (entity == null)
        {
            entity = ModuleEntity.registered(detected.type());
            this.repository.persist(entity);
        }
        entity.enabled = enabled;
        entity.updatedBy = user;
        return this.detected(detected, entity);
    }

    @Override
    @Transactional
    public void remove(String module)
    {
        ModuleEntity entity = this.repository.findByIdOptional(module)
            .orElseThrow(() -> new UnknownModuleException(module));
        if (this.detected.containsKey(module))
            throw new ModuleConflictException("Module " + module + " is detected, only a missing module can be removed");

        this.repository.delete(entity);
    }

    private ModuleMetadata detected(WombatModule module, ModuleEntity entity)
    {
        return new ModuleMetadata(
            module.type(),
            module.assetClass(),
            entity == null || entity.enabled,
            false,
            this.isExtension(module.getClass()) ? Source.extension : Source.core
        );
    }

    private static ModuleMetadata missing(ModuleEntity entity)
    {
        return new ModuleMetadata(entity.type(), null, entity.enabled, true, null);
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
