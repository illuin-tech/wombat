package tech.illuin.wombat.module.extension;

import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusModule;
import tech.illuin.wombat.module.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;

import java.util.ArrayList;
import java.util.List;

public class CoreExtensionLoader implements ExtensionLoader
{
    private final WombatModuleProperties properties;

    public CoreExtensionLoader(WombatModuleProperties properties)
    {
        this.properties = properties;
    }

    @Override
    public List<WombatModule> load()
    {
        List<WombatModule> modules = new ArrayList<>();

        modules.add(new KubernetesSimulatedModule());
        modules.add(new LLMSimulatedModule());

        if (this.properties.llmPrometheus().enabled())
            modules.add(new LLMPrometheusModule());
        if (this.properties.llmStatic().enabled())
            modules.add(new LLMStaticModule());
        if (this.properties.kubernetesApi().enabled())
            modules.add(new KubernetesAPIModule());

        return modules;
    }
}
