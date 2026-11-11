package tech.illuin.wombat.engine.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.connector.boavizta.connector.BoaviztaClient;
import tech.illuin.wombat.core.connector.ecologits.connector.EcologitsClient;
import tech.illuin.wombat.core.evaluation.impact.kubernetes.KubernetesMetricResolver;
import tech.illuin.wombat.core.evaluation.impact.llm.LLMMetricResolver;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.engine.module.extension.CompositeExtensionLoader;
import tech.illuin.wombat.engine.module.extension.ExtensionLoader;
import tech.illuin.wombat.engine.module.extension.WombatModuleProperties;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tech.illuin.wombat.module.kubernetes_static.KubernetesStaticModule;
import tech.illuin.wombat.module.llm_anthropic.LLMAnthropicModule;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusModule;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;
import tech.illuin.wombat.module.llm_vertexai.LLMVertexAIModule;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the producers directly rather than through CDI: the toggles are plain logic over
 * {@link WombatModuleProperties}, and the collaborators are never touched during instantiation.
 */
class WombatModuleConfigTest
{

    private final WombatModuleConfig config = new WombatModuleConfig();

    @Test
    void provideWombatExtensionLoader_returnsCompositeExtensionLoader()
    {
        WombatModuleProperties properties = properties(true, true, true, true);
        ExtensionLoader loader = this.config.provideWombatExtensionLoader(properties);

        assertInstanceOf(CompositeExtensionLoader.class, loader);
    }

    @Test
    void provideWombatModules_loadsModulesFromExtensionLoader()
    {
        ExtensionLoader loader = mock(ExtensionLoader.class);
        List<WombatModule> expected = List.of(new KubernetesAPIModule(), new LLMStaticModule());
        when(loader.load()).thenReturn(expected);

        List<WombatModule> result = this.config.provideWombatModules(loader);

        assertEquals(expected, result);
        verify(loader).load();
    }

    @Test
    void provideModuleRegistry_usesConfigurationForEnabledMetadata()
    {
        List<WombatModule> modules = List.of(
            new KubernetesAPIModule(),
            new LLMPrometheusModule(),
            new LLMAnthropicModule(),
            new LLMVertexAIModule(),
            new KubernetesSimulatedModule(),
            new LLMSimulatedModule(),
            new KubernetesStaticModule(),
            new LLMStaticModule()
        );

        assertAll(
            () -> assertEnabled(this.config.provideModuleRegistry(modules, properties(false, true, true, true)), new KubernetesAPIModule(), false),
            () -> assertEnabled(this.config.provideModuleRegistry(modules, properties(true, false, true, true)), new LLMPrometheusModule(), false),
            () -> assertEnabled(this.config.provideModuleRegistry(modules, properties(true, true, false, true)), new LLMAnthropicModule(), false),
            () -> assertEnabled(this.config.provideModuleRegistry(modules, properties(true, true, true, false)), new LLMVertexAIModule(), false),
            () -> assertEnabled(this.config.provideModuleRegistry(modules, properties(false, false, false, false)), new KubernetesSimulatedModule(), true),
            () -> assertEnabled(this.config.provideModuleRegistry(modules, properties(false, false, false, false)), new LLMSimulatedModule(), true),
            () -> assertEnabled(this.config.provideModuleRegistry(modules, properties(false, false, false, false)), new KubernetesStaticModule(), true),
            () -> assertEnabled(this.config.provideModuleRegistry(modules, properties(false, false, false, false)), new LLMStaticModule(), true)
        );
    }

    private static void assertEnabled(ModuleRegistry registry, WombatModule module, boolean expected)
    {
        ModuleMetadata metadata = registry.getModule(module.type().name()).orElseThrow();
        assertEquals(expected, metadata.enabled(), "enabled metadata for " + metadata.type().name());
    }

    @Test
    void provideMappers_registerSubtypesForGivenModules()
    {
        List<WombatModule> modules = List.of(
            new KubernetesAPIModule(),
            new LLMPrometheusModule(),
            new LLMStaticModule()
        );

        JsonMapper jsonMapper = this.config.provideJsonMapper(modules);
        assertNotNull(jsonMapper);

        YAMLMapper yamlMapper = this.config.provideYAMLMapper(modules);
        assertNotNull(yamlMapper);
    }

    @Test
    void yamlMapper_deserializesKebabCaseAndIgnoresUnknownProperties() throws Exception
    {
        YAMLMapper yamlMapper = this.config.provideYAMLMapper(List.of());
        String yaml = """
            first-name: Alice
            home-address: 123 Main St
            unknown-extra-field: should-be-ignored
            """;

        SampleRecord result = yamlMapper.readValue(yaml, SampleRecord.class);
        assertEquals("Alice", result.firstName());
        assertEquals("123 Main St", result.homeAddress());
    }

    @Test
    void jsonMapper_deserializesCamelCaseAndIgnoresUnknownProperties() throws Exception
    {
        JsonMapper jsonMapper = this.config.provideJsonMapper(List.of());
        String json = """
            {
                "firstName": "Bob",
                "homeAddress": "456 Side St",
                "unknownExtraField": "should-be-ignored"
            }
            """;

        SampleRecord result = jsonMapper.readValue(json, SampleRecord.class);
        assertEquals("Bob", result.firstName());
        assertEquals("456 Side St", result.homeAddress());
    }

    @Test
    void configureQuarkusObjectMapper_ignoresUnknownProperties() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        this.config.configureQuarkusObjectMapper(null, mapper, List.of());
        String json = """
            {
                "firstName": "Charlie",
                "homeAddress": "789 Broadway",
                "unknownField": 12345
            }
            """;

        SampleRecord result = mapper.readValue(json, SampleRecord.class);
        assertEquals("Charlie", result.firstName());
        assertEquals("789 Broadway", result.homeAddress());
    }

    private record SampleRecord(String firstName, String homeAddress) {}

    @Test
    void provideDefaults_createsHandlersForFamilies()
    {
        KubernetesMetricResolver k8sResolver = mock(KubernetesMetricResolver.class);
        BoaviztaClient boaviztaClient = mock(BoaviztaClient.class);
        WombatModuleConfig.DefaultHandlers k8sHandlers = WombatModuleConfig.provideKubernetesDefaults(k8sResolver, boaviztaClient);

        assertEquals(ServiceFamily.KUBERNETES_CONTAINER, k8sHandlers.family());
        assertNotNull(k8sHandlers.activityResolver());
        assertNotNull(k8sHandlers.impactResolver());
        assertNotNull(k8sHandlers.costResolver());

        LLMMetricResolver llmResolver = mock(LLMMetricResolver.class);
        EcologitsClient ecologitsClient = mock(EcologitsClient.class);
        WombatModuleConfig.DefaultHandlers llmHandlers = WombatModuleConfig.provideLLMDefaults(llmResolver, ecologitsClient);

        assertEquals(ServiceFamily.LLM, llmHandlers.family());
        assertNotNull(llmHandlers.activityResolver());
        assertNotNull(llmHandlers.impactResolver());
        assertNotNull(llmHandlers.costResolver());
    }

    private static WombatModuleProperties properties(boolean kubernetesApi, boolean llmPrometheus, boolean llmAnthropic, boolean vertexAi)
    {
        return new WombatModuleProperties()
        {
            @Override
            public KubernetesApiProperties kubernetesApi()
            {
                return () -> kubernetesApi;
            }

            @Override
            public LlmPrometheusProperties llmPrometheus()
            {
                return () -> llmPrometheus;
            }

            @Override
            public LlmAnthropicProperties llmAnthropic()
            {
                return () -> llmAnthropic;
            }

            @Override
            public LlmVertexAIProperties llmVertexAi()
            {
                return () -> vertexAi;
            }

            @Override
            public ExtensionProperties extension()
            {
                return () -> Path.of("extensions");
            }
        };
    }
}
