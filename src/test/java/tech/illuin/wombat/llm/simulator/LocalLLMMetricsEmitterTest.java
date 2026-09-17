package tech.illuin.wombat.llm.simulator;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalLLMMetricsEmitterTest
{

    @Test
    void emit_incrementsCounterPerModelTaggedByModelName()
    {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LocalLLMMetricsEmitter emitter = new LocalLLMMetricsEmitter(
            registry, properties("generation.tokens", List.of(model("m1", 50, 50), model("m2", 10, 10))));

        emitter.emit();
        emitter.emit();

        assertEquals(100.0, registry.get("generation.tokens").tag("model_name", "m1").counter().count(), 1e-9);
        assertEquals(20.0, registry.get("generation.tokens").tag("model_name", "m2").counter().count(), 1e-9);
    }

    @Test
    void emit_tokensStayWithinConfiguredRange()
    {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LocalLLMMetricsEmitter emitter = new LocalLLMMetricsEmitter(
            registry, properties("generation.tokens", List.of(model("m", 100, 200))));

        emitter.emit();

        double count = registry.get("generation.tokens").tag("model_name", "m").counter().count();
        assertTrue(count >= 100 && count <= 200, "expected token count in [100, 200] but was " + count);
    }

    @Test
    void emit_noModels_doesNothing()
    {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LocalLLMMetricsEmitter emitter = new LocalLLMMetricsEmitter(registry, properties("generation.tokens", List.of()));

        emitter.emit();

        assertTrue(registry.getMeters().isEmpty());
    }

    private static LLMSimulatorProperties properties(String metricName, List<LLMSimulatorProperties.Model> models)
    {
        return new LLMSimulatorProperties()
        {
            @Override
            public boolean enabled()
            {
                return true;
            }

            @Override
            public String cron()
            {
                return "*/10 * * * * ?";
            }

            @Override
            public String metricName()
            {
                return metricName;
            }

            @Override
            public List<Model> models()
            {
                return models;
            }
        };
    }

    private static LLMSimulatorProperties.Model model(String name, int minTokens, int maxTokens)
    {
        return new LLMSimulatorProperties.Model()
        {
            @Override
            public String name()
            {
                return name;
            }

            @Override
            public int minTokens()
            {
                return minTokens;
            }

            @Override
            public int maxTokens()
            {
                return maxTokens;
            }
        };
    }
}
