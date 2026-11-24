package tech.illuin.wombat.engine.module.extension;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppExtensionLoaderTest
{
    @Test
    void loadsTheModulesOfTheApp()
    {
        List<WombatModule> modules = new AppExtensionLoader().load();

        assertEquals(2, modules.size());
        assertTrue(modules.stream().anyMatch(KubernetesSimulatedModule.class::isInstance));
        assertTrue(modules.stream().anyMatch(LLMSimulatedModule.class::isInstance));
    }
}
