package tech.illuin.wombat.persistence.micrometer;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.impact.llm.LLMModelMetricRepository;

import java.time.Duration;
import java.util.concurrent.Executors;

@ApplicationScoped
public class MetricsConfig
{
    public static final Duration SAMPLING = Duration.ofMinutes(5);

    public static final Duration COMPACTION = Duration.ofHours(1);

    public static final long COMPACTION_MILLIS = COMPACTION.toMillis();

    public static final Duration COMPACTION_DELAY = Duration.ofMinutes(1);

    private static final Logger logger = LoggerFactory.getLogger(MetricsConfig.class);

    @Singleton
    public WombatStepMeterRegistry provideStepRegistry(
        KubernetesMetricRepository kubernetesMetricRepository,
        LLMModelMetricRepository llmModelMetricRepository
    ) {
        StepRegistryConfig stepConfig = new StepRegistryConfig()
        {
            @Override
            public String prefix()
            {
                return "wombat";
            }

            @Override
            public Duration step()
            {
                return SAMPLING;
            }

            @Override
            public String get(@NonNull String key)
            {
                return null;
            }
        };
        return new WombatStepMeterRegistry(stepConfig, Clock.SYSTEM, kubernetesMetricRepository, llmModelMetricRepository);
    }

    void onStart(
        @Observes StartupEvent event,
        MeterRegistry rootRegistry,
        WombatStepMeterRegistry stepRegistry
    ) {
        if (!(rootRegistry instanceof CompositeMeterRegistry composite))
            throw new IllegalArgumentException("Root registry is not composite");

        logger.info("Root MeterRegistry implementation: {}", rootRegistry.getClass().getName());
        stepRegistry.start(Executors.defaultThreadFactory());
        composite.add(stepRegistry);
        logger.info("Attached SqliteStepMeterRegistry to CompositeMeterRegistry (step={})", SAMPLING);
    }

    void onStop(@Observes ShutdownEvent event, WombatStepMeterRegistry stepRegistry)
    {
        stepRegistry.close();
    }
}
