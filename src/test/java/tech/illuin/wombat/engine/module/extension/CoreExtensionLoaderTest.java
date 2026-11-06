package tech.illuin.wombat.engine.module.extension;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.module.kubernetes_static.KubernetesStaticModule;
import tech.illuin.wombat.module.llm_anthropic.LLMAnthropicModule;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusModule;
import tech.illuin.wombat.module.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;
import tech.illuin.wombat.module.llm_vertexai.LLMVertexAIModule;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoreExtensionLoaderTest
{
    @Test
    void alwaysLoadsSimulatedModulesEvenWhenAllCoreModulesDisabled()
    {
        WombatModuleProperties properties = properties(false, false, false, false);
        try (CoreExtensionLoader loader = new CoreExtensionLoader(properties))
        {
            List<WombatModule> modules = loader.load();

            assertEquals(4, modules.size());
            assertTrue(modules.stream().anyMatch(KubernetesSimulatedModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(LLMSimulatedModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(LLMStaticModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(KubernetesStaticModule.class::isInstance));
            assertFalse(modules.stream().anyMatch(KubernetesAPIModule.class::isInstance));
            assertFalse(modules.stream().anyMatch(LLMPrometheusModule.class::isInstance));
            assertFalse(modules.stream().anyMatch(LLMAnthropicModule.class::isInstance));
            assertFalse(modules.stream().anyMatch(LLMVertexAIModule.class::isInstance));
        }
        catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void loadsAllModulesWhenAllTogglesEnabled()
    {
        WombatModuleProperties properties = properties(true, true, true, true);
        try (CoreExtensionLoader loader = new CoreExtensionLoader(properties))
        {
            List<WombatModule> modules = loader.load();

            assertEquals(8, modules.size());
            assertTrue(modules.stream().anyMatch(KubernetesSimulatedModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(LLMSimulatedModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(KubernetesAPIModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(LLMPrometheusModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(LLMStaticModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(KubernetesStaticModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(LLMAnthropicModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(LLMVertexAIModule.class::isInstance));
        }
        catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void loadsOnlySpecificallyEnabledModules()
    {
        WombatModuleProperties properties = properties(true, false, false, false);
        try (CoreExtensionLoader loader = new CoreExtensionLoader(properties))
        {
            List<WombatModule> modules = loader.load();

            assertEquals(5, modules.size());
            assertTrue(modules.stream().anyMatch(KubernetesSimulatedModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(LLMSimulatedModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(LLMStaticModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(KubernetesStaticModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(KubernetesAPIModule.class::isInstance));
            assertFalse(modules.stream().anyMatch(LLMPrometheusModule.class::isInstance));
            assertFalse(modules.stream().anyMatch(LLMAnthropicModule.class::isInstance));
            assertFalse(modules.stream().anyMatch(LLMVertexAIModule.class::isInstance));
        }
        catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void wrapsAnthropicModuleWithCacheWhenCacheEnabled()
    {
        try (CoreExtensionLoader loader = new CoreExtensionLoader(properties(false, false, true, false, true)))
        {
            List<WombatModule> modules = loader.load();

            assertTrue(modules.stream().anyMatch(CachedLLMAnthropicModule.class::isInstance));
        }
        catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void loadsPlainAnthropicModuleWhenCacheDisabled()
    {
        try (CoreExtensionLoader loader = new CoreExtensionLoader(properties(false, false, true, false, false)))
        {
            List<WombatModule> modules = loader.load();

            assertTrue(modules.stream().anyMatch(LLMAnthropicModule.class::isInstance));
            assertFalse(modules.stream().anyMatch(CachedLLMAnthropicModule.class::isInstance));
        }
        catch (IOException e) {
            fail(e);
        }
    }

    private static WombatModuleProperties properties(boolean kubernetesApi, boolean llmPrometheus, boolean llmAnthropic, boolean vertexAi)
    {
        return properties(kubernetesApi, llmPrometheus, llmAnthropic, vertexAi, false);
    }

    private static WombatModuleProperties properties(boolean kubernetesApi, boolean llmPrometheus, boolean llmAnthropic, boolean vertexAi, boolean anthropicCache)
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
                return anthropic(llmAnthropic, anthropicCache);
            }

            @Override
            public LlmVertexAIProperties llmVertexAi()
            {
                return () -> vertexAi;
            }

            @Override
            public ExtensionProperties extension()
            {
                return new ExtensionProperties() {
                    @Override
                    public Path path()
                    {
                        return Path.of("extensions");
                    }
                };
            }
        };
    }

    private static WombatModuleProperties.LlmAnthropicProperties anthropic(boolean enabled, boolean cacheEnabled)
    {
        return new WombatModuleProperties.LlmAnthropicProperties()
        {
            @Override
            public boolean enabled()
            {
                return enabled;
            }

            @Override
            public WombatModuleProperties.CacheProperties cache()
            {
                return new WombatModuleProperties.CacheProperties()
                {
                    @Override
                    public boolean enabled()
                    {
                        return cacheEnabled;
                    }

                    @Override
                    public long maximumSize()
                    {
                        return 100;
                    }

                    @Override
                    public Duration expireAfterWrite()
                    {
                        return Duration.ofMinutes(30);
                    }
                };
            }
        };
    }
}
