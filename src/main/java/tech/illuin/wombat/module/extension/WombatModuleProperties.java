package tech.illuin.wombat.module.extension;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.nio.file.Path;

@ConfigMapping(prefix = "module")
public interface WombatModuleProperties
{
    @WithName("kubernetes-api")
    KubernetesApiProperties kubernetesApi();

    @WithName("llm-prometheus")
    LlmPrometheusProperties llmPrometheus();

    @WithName("llm-static")
    LlmStaticProperties llmStatic();

    @WithName("extension")
    ExtensionProperties extension();

    interface KubernetesApiProperties
    {
        @WithDefault("true")
        boolean enabled();
    }

    interface LlmPrometheusProperties
    {
        @WithDefault("true")
        boolean enabled();
    }

    interface LlmStaticProperties
    {
        @WithDefault("true")
        boolean enabled();
    }

    interface ExtensionProperties
    {
        @WithName("path")
        @WithDefault("extensions")
        Path path();
    }
}
