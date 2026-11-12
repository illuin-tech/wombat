package tech.illuin.wombat.engine.module.extension;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.module.kubernetes_static.KubernetesStaticModule;
import tech.illuin.wombat.module.llm_anthropic.LLMAnthropicModule;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusModule;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;
import tech.illuin.wombat.module.llm_vertexai.LLMVertexAIModule;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoreExtensionLoaderTest
{
    @Test
    void loadsAllCoreModules()
    {
        List<WombatModule> modules = new CoreExtensionLoader().load();

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
}
