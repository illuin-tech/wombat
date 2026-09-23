package tech.illuin.wombat.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.quarkus.arc.All;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import tech.illuin.wombat.module.api.ModuleRegistration;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusModule;
import tech.illuin.wombat.module.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;

import java.util.List;

/**
 * CDI wiring for the SDK modules.
 * <p>
 * The SDK artifacts (wombat-core, wombat-module) stay CDI-free plain Java; the beans exposing them to the app live
 * here. Modules are stateless — they build their sources and impact-resolvers per
 * {@link tech.illuin.wombat.core.context.WombatContext} — so a single instance per module is enough.
 * <p>
 * Each module is produced as its own {@link ModuleRegistration}, enabled or disabled according to
 * {@link WombatModuleProperties}, and the assembled {@link WombatModule} list is what
 * {@link tech.illuin.wombat.core.WombatCore} consumes.
 */
@ApplicationScoped
public class WombatModuleConfig
{
    private static final Logger logger = LoggerFactory.getLogger(WombatModuleConfig.class);

    private static final String MODULE_KUBERNETES_API = "kubernetes-api";
    private static final String MODULE_KUBERNETES_SIMULATION = "kubernetes-simulation";
    private static final String MODULE_LLM_PROMETHEUS = "llm-prometheus";
    private static final String MODULE_LLM_STATIC = "llm-static";
    private static final String MODULE_LLM_SIMULATION = "llm-simulation";
    private static final WombatEvaluationResolver NOOP_COST_RESOLVER = (Asset asset, ActivityData _) -> new AssetCost(asset.environmentId(), asset.id());

    @Singleton
    public static DefaultHandlers provideKubernetesDefaults(KubernetesMetricResolver metricResolver, BoaviztaClient boaviztaClient)
    {
        return new DefaultHandlers(
            ServiceFamily.KUBERNETES_CONTAINER,
            new KubernetesActivityResolver(metricResolver),
            new BoaviztaEvaluationResolver(boaviztaClient),
            NOOP_COST_RESOLVER
        );
    }

    @Singleton
    public static DefaultHandlers provideLLMDefaults(LLMMetricResolver metricResolver, EcologitsClient ecologitsClient)
    {
        return new DefaultHandlers(
            ServiceFamily.LLM,
            new LLMActivityResolver(metricResolver),
            new EcologitsEvaluationResolver(ecologitsClient),
            NOOP_COST_RESOLVER
        );
    }

    @Singleton @CoreModule
    public ModuleRegistration provideKubernetesAPIModule(WombatModuleProperties properties)
    {
        if (!properties.kubernetesApi().enabled())
            return ModuleRegistration.disabled(MODULE_KUBERNETES_API);

        return ModuleRegistration.of(MODULE_KUBERNETES_API, new KubernetesAPIModule());
    }

    @Singleton @CoreModule
    public ModuleRegistration provideKubernetesSimulationModule()
    {
        return ModuleRegistration.of(MODULE_KUBERNETES_SIMULATION, new KubernetesSimulatedModule());
    }

    @Singleton @CoreModule
    public ModuleRegistration provideLLMPrometheusModule(WombatModuleProperties properties)
    {
        if (!properties.llmPrometheus().enabled())
            return ModuleRegistration.disabled(MODULE_LLM_PROMETHEUS);

        return ModuleRegistration.of(MODULE_LLM_PROMETHEUS, new LLMPrometheusModule());
    }

    @Singleton @CoreModule
    public ModuleRegistration provideLLMStaticModule(WombatModuleProperties properties)
    {
        if (!properties.llmStatic().enabled())
            return ModuleRegistration.disabled(MODULE_LLM_STATIC);

        return ModuleRegistration.of(MODULE_LLM_STATIC, new LLMStaticModule());
    }

    @Singleton @CoreModule
    public ModuleRegistration provideLLMSimulationModule()
    {
        return ModuleRegistration.of(MODULE_LLM_SIMULATION, new LLMSimulatedModule());
    }

    @Singleton
    public List<WombatModule> provideWombatModules(@All @CoreModule List<ModuleRegistration> registrations)
    {
        List<WombatModule> modules = registrations.stream()
            .filter(WombatModuleConfig::isMounted)
            .flatMap(registration -> registration.modules().stream())
            .toList();

        //TODO: extension module dynamic loading

        logger.info("Wiring {} wombat module(s): {}", modules.size(), modules.stream().map(module -> module.getClass().getSimpleName()).toList());
        return modules;
    }

    /**
     * Takes the assembled {@link #provideWombatModules} list, not {@code @All List<WombatModule>}: the modules are
     * not beans themselves — they are carried inside {@link ModuleRegistration} beans — so {@code @All} would find
     * none and quietly hand back a mapper that knows no asset subtype at all.
     * <p>
     * {@code @Typed} keeps these two out of every {@code ObjectMapper} injection point.
     * <p>
     * Both mappers extend {@link ObjectMapper}, so without it they join Quarkus' own {@code ObjectMapperProducer} as
     * candidates wherever a plain {@code ObjectMapper} is injected — the REST-client serialisers among them — and the
     * build fails on an ambiguous dependency. Restricting the bean types leaves each resolvable only as itself.
     */

    public void configureQuarkusObjectMapper(@Observes StartupEvent event, ObjectMapper mapper, List<WombatModule> modules)
    {
        registerSubTypes(modules, mapper);
    }

    @Singleton
    @Typed(YAMLMapper.class)
    public YAMLMapper provideYAMLMapper(List<WombatModule> modules)
    {
        YAMLMapper mapper = YAMLMapper.builder().findAndAddModules().build();
        registerSubTypes(modules, mapper);
        return mapper;
    }

    @Singleton
    @Typed(JsonMapper.class)
    public JsonMapper provideJsonMapper(List<WombatModule> modules)
    {
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        registerSubTypes(modules, mapper);
        return mapper;
    }

    private static boolean isMounted(ModuleRegistration registration)
    {
        if (!registration.enabled())
        {
            logger.info("Skipping disabled module {}", registration.id());
            return false;
        }
        return true;
    }

    private static <O extends ObjectMapper> void registerSubTypes(List<WombatModule> modules, O mapper)
    {
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
