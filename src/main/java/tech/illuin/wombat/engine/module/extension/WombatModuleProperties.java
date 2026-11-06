package tech.illuin.wombat.engine.module.extension;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.nio.file.Path;
import java.time.Duration;

@ConfigMapping(prefix = "module")
public interface WombatModuleProperties
{
    @WithName("kubernetes-api")
    KubernetesApiProperties kubernetesApi();

    @WithName("llm-prometheus")
    LlmPrometheusProperties llmPrometheus();

    @WithName("llm-anthropic")
    LlmAnthropicProperties llmAnthropic();

    @WithName("llm-vertex-ai")
    LlmVertexAIProperties llmVertexAi();

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

    interface LlmAnthropicProperties
    {
        @WithDefault("true")
        boolean enabled();

        CacheProperties cache();
    }

    interface CacheProperties
    {
        @WithDefault("true")
        boolean enabled();

        @WithName("maximum-size")
        @WithDefault("1000")
        long maximumSize();

        @WithName("expire-after-write")
        @WithDefault("30m")
        Duration expireAfterWrite();
    }

    interface LlmVertexAIProperties
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
