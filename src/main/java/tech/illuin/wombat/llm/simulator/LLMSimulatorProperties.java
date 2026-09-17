package tech.illuin.wombat.llm.simulator;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.List;

@ConfigMapping(prefix = "llm-simulator")
public interface LLMSimulatorProperties
{

    @WithDefault("false")
    boolean enabled();

    @WithDefault("*/10 * * * * ?")
    String cron();

    @WithName("metric-name")
    @WithDefault("generation.tokens")
    String metricName();

    List<Model> models();

    interface Model
    {

        String name();

        @WithName("min-tokens")
        @WithDefault("100")
        int minTokens();

        @WithName("max-tokens")
        @WithDefault("2000")
        int maxTokens();
    }
}
