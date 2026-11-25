package tech.illuin.wombat.engine.module.extension;

import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;

import java.util.List;

/**
 * The modules the app defines itself, in {@code engine.module.library}.
 */
public class AppExtensionLoader implements ExtensionLoader
{
    @Override
    public List<WombatModule> load()
    {
        return List.of(
            new KubernetesSimulatedModule(),
            new LLMSimulatedModule()
        );
    }
}
