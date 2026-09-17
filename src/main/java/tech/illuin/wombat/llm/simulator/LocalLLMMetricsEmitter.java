package tech.illuin.wombat.llm.simulator;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

public class LocalLLMMetricsEmitter
{
    private static final Logger logger = LoggerFactory.getLogger(LocalLLMMetricsEmitter.class);

    private final MeterRegistry registry;
    private final LLMSimulatorProperties properties;

    public LocalLLMMetricsEmitter(MeterRegistry registry, LLMSimulatorProperties properties)
    {
        this.registry = registry;
        this.properties = properties;
    }

    @Scheduled(cron = "${llm-simulator.cron}")
    public void emit()
    {
        for (LLMSimulatorProperties.Model model : this.properties.models())
        {
            int bound = Math.max(model.minTokens(), model.maxTokens()) + 1;
            long tokens = ThreadLocalRandom.current().nextLong(model.minTokens(), bound);
            this.counter(model.name()).increment(tokens);
            logger.trace("Simulated {} output tokens for model {}", tokens, model.name());
        }
    }

    private Counter counter(String modelName)
    {
        return Counter.builder(this.properties.metricName())
            .tag("model_name", modelName)
            .register(this.registry);
    }
}
