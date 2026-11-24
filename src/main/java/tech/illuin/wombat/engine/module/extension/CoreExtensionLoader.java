package tech.illuin.wombat.engine.module.extension;

import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.module.boavizta.BoaviztaModule;
import tech.illuin.wombat.module.ecologits.EcologitsModule;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.kubernetes_metrics.KubernetesMetricsModule;
import tech.illuin.wombat.module.kubernetes_static.KubernetesStaticModule;
import tech.illuin.wombat.module.llm_anthropic.LLMAnthropicModule;
import tech.illuin.wombat.module.llm_metrics.LLMMetricsModule;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusModule;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;
import tech.illuin.wombat.module.llm_vertexai.LLMVertexAIModule;

import java.util.List;

public class CoreExtensionLoader implements ExtensionLoader
{
    @Override
    public List<WombatModule> load()
    {
        return List.of(
            new KubernetesSimulatedModule(),
            new LLMSimulatedModule(),
            new KubernetesStaticModule(),
            new LLMStaticModule(),
            new LLMPrometheusModule(),
            new KubernetesAPIModule(),
            new LLMAnthropicModule(),
            new LLMVertexAIModule(),
            /* Family modules, tried in this order among those serving the same family, extensions after them */
            new KubernetesMetricsModule(),
            new LLMMetricsModule(),
            new BoaviztaModule(),
            new EcologitsModule()
        );
    }
}
