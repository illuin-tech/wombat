package tech.illuin.wombat.module.extension;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusModule;
import tech.illuin.wombat.module.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoreExtensionLoaderTest
{
    @Test
    void alwaysLoadsSimulatedModulesEvenWhenAllCoreModulesDisabled()
    {
        WombatModuleProperties properties = properties(false, false, false);
        try (CoreExtensionLoader loader = new CoreExtensionLoader(properties))
        {
            List<WombatModule> modules = loader.load();

            assertEquals(2, modules.size());
            assertTrue(modules.stream().anyMatch(KubernetesSimulatedModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(LLMSimulatedModule.class::isInstance));
            assertFalse(modules.stream().anyMatch(KubernetesAPIModule.class::isInstance));
            assertFalse(modules.stream().anyMatch(LLMPrometheusModule.class::isInstance));
            assertFalse(modules.stream().anyMatch(LLMStaticModule.class::isInstance));
        }
        catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void loadsAllModulesWhenAllTogglesEnabled()
    {
        WombatModuleProperties properties = properties(true, true, true);
        try (CoreExtensionLoader loader = new CoreExtensionLoader(properties))
        {
            List<WombatModule> modules = loader.load();

            assertEquals(5, modules.size());
            assertTrue(modules.stream().anyMatch(KubernetesSimulatedModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(LLMSimulatedModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(KubernetesAPIModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(LLMPrometheusModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(LLMStaticModule.class::isInstance));
        }
        catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void loadsOnlySpecificallyEnabledModules()
    {
        WombatModuleProperties properties = properties(true, false, false);
        try (CoreExtensionLoader loader = new CoreExtensionLoader(properties))
        {
            List<WombatModule> modules = loader.load();

            assertEquals(3, modules.size());
            assertTrue(modules.stream().anyMatch(KubernetesSimulatedModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(LLMSimulatedModule.class::isInstance));
            assertTrue(modules.stream().anyMatch(KubernetesAPIModule.class::isInstance));
            assertFalse(modules.stream().anyMatch(LLMPrometheusModule.class::isInstance));
            assertFalse(modules.stream().anyMatch(LLMStaticModule.class::isInstance));
        }
        catch (IOException e) {
            fail(e);
        }
    }

    private static WombatModuleProperties properties(boolean kubernetesApi, boolean llmPrometheus, boolean llmStatic)
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
            public LlmStaticProperties llmStatic()
            {
                return () -> llmStatic;
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
}
