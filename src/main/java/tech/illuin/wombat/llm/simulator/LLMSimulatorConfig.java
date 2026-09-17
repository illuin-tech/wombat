package tech.illuin.wombat.llm.simulator;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

@ApplicationScoped
public class LLMSimulatorConfig
{

    @Singleton
    @IfBuildProperty(name = "llm-simulator.enabled", stringValue = "true")
    public LocalLLMMetricsEmitter provideLocalLLMMetricsEmitter(MeterRegistry registry, LLMSimulatorProperties properties)
    {
        return new LocalLLMMetricsEmitter(registry, properties);
    }
}
