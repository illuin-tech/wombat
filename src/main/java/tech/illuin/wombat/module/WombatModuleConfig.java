package tech.illuin.wombat.module;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.context.model.UnrecognizedAssetProblemHandler;
import tech.illuin.wombat.core.activity.WombatActivityResolver;
import tech.illuin.wombat.core.activity.commons.ActivityData;
import tech.illuin.wombat.core.activity.kubernetes.KubernetesActivityResolver;
import tech.illuin.wombat.core.activity.llm.LLMActivityResolver;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.ServiceFamily;
import tech.illuin.wombat.core.connector.boavizta.connector.BoaviztaClient;
import tech.illuin.wombat.core.connector.boavizta.impact.BoaviztaEvaluationResolver;
import tech.illuin.wombat.core.connector.ecologits.connector.EcologitsClient;
import tech.illuin.wombat.core.connector.ecologits.impact.EcologitsEvaluationResolver;
import tech.illuin.wombat.core.evaluation.WombatEvaluationResolver;
import tech.illuin.wombat.core.evaluation.cost.commons.AssetCost;
import tech.illuin.wombat.core.evaluation.impact.kubernetes.KubernetesMetricResolver;
import tech.illuin.wombat.core.evaluation.impact.llm.LLMMetricResolver;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.module.extension.CompositeExtensionLoader;
import tech.illuin.wombat.module.extension.CoreExtensionLoader;
import tech.illuin.wombat.module.extension.DynamicExtensionLoader;
import tech.illuin.wombat.module.extension.ExtensionLoader;
import tech.illuin.wombat.module.extension.WombatModuleProperties;

import java.io.IOException;
import java.util.List;

/**
 * CDI wiring for the SDK modules.
 */
@ApplicationScoped
public class WombatModuleConfig
{
    private static final WombatEvaluationResolver NOOP_COST_RESOLVER = (Asset asset, ActivityData _) -> new AssetCost(asset.environmentId(), asset.id());
    private static final Logger logger = LoggerFactory.getLogger(WombatModuleConfig.class);

    @Produces @Singleton
    public static DefaultHandlers provideKubernetesDefaults(KubernetesMetricResolver metricResolver, BoaviztaClient boaviztaClient)
    {
        return new DefaultHandlers(
            ServiceFamily.KUBERNETES_CONTAINER,
            new KubernetesActivityResolver(metricResolver),
            new BoaviztaEvaluationResolver(boaviztaClient),
            NOOP_COST_RESOLVER
        );
    }

    @Produces @Singleton
    public static DefaultHandlers provideLLMDefaults(LLMMetricResolver metricResolver, EcologitsClient ecologitsClient)
    {
        return new DefaultHandlers(
            ServiceFamily.LLM,
            new LLMActivityResolver(metricResolver),
            new EcologitsEvaluationResolver(ecologitsClient),
            NOOP_COST_RESOLVER
        );
    }

    @Produces @Singleton
    public static ExtensionLoader provideWombatExtensionLoader(WombatModuleProperties properties)
    {
        return new CompositeExtensionLoader(
            new CoreExtensionLoader(properties),
            new DynamicExtensionLoader(properties.extension().path())
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

    public void configureQuarkusObjectMapper(@Observes StartupEvent event, ObjectMapper mapper, List<WombatModule> modules)
    {
        registerSubTypes(modules, mapper);
    }

    @Produces
    @Singleton
    @Typed(YAMLMapper.class)
    public YAMLMapper provideYAMLMapper(List<WombatModule> modules)
    {
        YAMLMapper mapper = YAMLMapper.builder().findAndAddModules().build();
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

    private static <O extends ObjectMapper> void registerSubTypes(List<WombatModule> modules, O mapper)
    {
        mapper.disable(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS);
        mapper.addHandler(new UnrecognizedAssetProblemHandler());
        modules.forEach(module -> mapper.registerSubtypes(new NamedType(
            module.assetClass(), module.type().name()
        )));
    }

    public record DefaultHandlers(
        ServiceFamily family,
        WombatActivityResolver activityResolver,
        WombatEvaluationResolver impactResolver,
        WombatEvaluationResolver costResolver
    ) {}
}
