package tech.illuin.wombat.engine.module;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Singleton;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.engine.context.asset.model.UnrecognizedAssetProblemHandler;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.engine.module.extension.AppExtensionLoader;
import tech.illuin.wombat.engine.module.extension.CompositeExtensionLoader;
import tech.illuin.wombat.engine.module.extension.CoreExtensionLoader;
import tech.illuin.wombat.engine.module.extension.DynamicExtensionLoader;
import tech.illuin.wombat.engine.module.extension.ExtensionLoader;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tech.illuin.wombat.engine.module.registry.schema.AssetSchemaRegistry;
import tech.illuin.wombat.engine.module.registry.schema.DefaultSchemaRegistry;
import tech.illuin.wombat.engine.module.settings.ModuleSettingsReader;

import java.io.IOException;
import java.util.List;

/**
 * CDI wiring for the SDK modules.
 */
@ApplicationScoped
public class WombatModuleConfig
{
    private static final Logger logger = LoggerFactory.getLogger(WombatModuleConfig.class);

    @Produces @Singleton
    public static ExtensionLoader provideWombatExtensionLoader(WombatProperties wombat)
    {
        /* Family modules are tried in this order */
        return new CompositeExtensionLoader(
            new CoreExtensionLoader(),
            new AppExtensionLoader(),
            new DynamicExtensionLoader(wombat.module().extension().path())
        );
    }

    /**
     * Releases the extension classloader on shutdown, and on every dev-mode live reload which recreates the bean.
     */
    public static void closeExtensionLoader(@Disposes ExtensionLoader extensionLoader)
    {
        try {
            extensionLoader.close();
        }
        catch (IOException e) {
            logger.warn("Failed to close extension loader", e);
        }
    }

    @Produces @Singleton
    public static List<WombatModule> provideWombatModules(ExtensionLoader extensionLoader)
    {
        List<WombatModule> modules = extensionLoader.load();

        logger.info("Wiring {} wombat module(s): {}", modules.size(), modules.stream().map(module -> module.getClass().getSimpleName()).toList());
        return modules;
    }

    /**
     * Every core deployment shares these instances, so they are closed here, once, rather than with a deployment.
     */
    public static void closeWombatModules(@Disposes List<WombatModule> modules)
    {
        modules.forEach(WombatModule::close);
    }

    @Produces @Singleton
    public static AssetSchemaRegistry provideAssetSchemaRegistry(ModuleRegistry moduleRegistry, ObjectMapper mapper)
    {
        return new DefaultSchemaRegistry(moduleRegistry, mapper);
    }

    /**
     * Reads with the YAML mapper, whose kebab-case names are those of the configuration.
     */
    @Produces @Singleton
    public static ModuleSettingsReader provideModuleSettingsReader(WombatProperties wombat, List<WombatModule> modules, YAMLMapper mapper, Validator validator)
    {
        return new ModuleSettingsReader(wombat.module().settings(), modules, mapper, validator);
    }

    public void configureQuarkusObjectMapper(@Observes StartupEvent event, ObjectMapper mapper, List<WombatModule> modules)
    {
        registerSubTypes(modules, mapper);
    }

    @Produces
    @Singleton
    @Typed(YAMLMapper.class)
    public YAMLMapper provideYAMLMapper(List<WombatModule> modules)
    {
        YAMLMapper mapper = YAMLMapper.builder()
            .findAndAddModules()
            .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .build();
        registerSubTypes(modules, mapper);
        return mapper;
    }

    @Produces
    @Singleton
    @Typed(JsonMapper.class)
    public JsonMapper provideJsonMapper(List<WombatModule> modules)
    {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        registerSubTypes(modules, mapper);
        return mapper;
    }

    /**
     * Every detected module is registered with its settings, disabled ones too, so their assets are still read as such
     * rather than as unrecognized ones. Taken from the detected modules rather than the registry, which reads the
     * database: the YAML mapper is needed at startup, before the migrations run.
     */
    private static <O extends ObjectMapper> void registerSubTypes(List<WombatModule> modules, O mapper)
    {
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS);
        mapper.addHandler(new UnrecognizedAssetProblemHandler());
        for (WombatModule module : modules)
        {
            module.assetClass().ifPresent(assetClass -> mapper.registerSubtypes(new NamedType(assetClass, module.type().name())));
            module.settingsClass().ifPresent(settingsClass -> mapper.registerSubtypes(new NamedType(settingsClass, module.type().name())));
        }
    }
}
