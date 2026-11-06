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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.illuin.wombat.commons.NodeMode;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.persistence.backend.PersistenceEngine;
import tech.illuin.wombat.persistence.backend.push.PushBackendConfig;
import tech.illuin.wombat.persistence.store.MetricStores;

import java.time.Duration;
import java.util.concurrent.Executors;

@ApplicationScoped
public class MetricsConfig
{
    public static final Duration SAMPLING = Duration.ofMinutes(5);
    private static final Logger logger = LoggerFactory.getLogger(MetricsConfig.class);

    @Singleton
    public WombatStepMeterRegistry provideStepRegistry(
        PersistenceEngine engine,
        WombatProperties wombat,
        @ConfigProperty(name = "quarkus.datasource.db-kind") String dbKind
    ) {
        NodeMode mode = wombat.mode();
        String backend = mode == NodeMode.WORKER ? PushBackendConfig.BACKEND_PUSH : dbKind;
        MetricStores stores = engine.stores(backend);
        logger.info("Running in {} mode, sampled metrics are written to the {} backend", mode, backend);

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
        return new WombatStepMeterRegistry(stepConfig, Clock.SYSTEM, stores);
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
