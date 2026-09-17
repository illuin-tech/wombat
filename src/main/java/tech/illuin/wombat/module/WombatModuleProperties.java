package tech.illuin.wombat.module;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "module")
public interface WombatModuleProperties
{
    ModuleProperties kubernetesApi();

    ModuleProperties llmPrometheus();

    ModuleProperties llmStatic();

    interface ModuleProperties
    {
        @WithDefault("true")
        boolean enabled();
    }
}
