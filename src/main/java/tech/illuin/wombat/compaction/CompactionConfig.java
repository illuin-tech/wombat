package tech.illuin.wombat.compaction;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.PersistenceException;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.impact.llm.LLMModelMetricRepository;

import java.time.Instant;

@ApplicationScoped
public class CompactionConfig
{
    private static final Logger logger = LoggerFactory.getLogger(CompactionConfig.class);

    @Singleton
    public MetricCompactor provideMetricCompactor(
        KubernetesMetricRepository kubernetesRepository,
        LLMModelMetricRepository llmRepository
    ) {
        return new MetricCompactor(kubernetesRepository, llmRepository);
    }

    @Singleton
    public CompactionService provideCompactionService(MetricCompactor compactor)
    {
        return new CompactionService(compactor);
    }

    void onStart(@Observes StartupEvent event, MetricCompactor compactor)
    {
        boolean scheduled = ConfigProvider.getConfig()
            .getOptionalValue("quarkus.scheduler.enabled", Boolean.class)
            .orElse(true);
        if (!scheduled)
            return;

        Thread.ofVirtual().name("metric-compaction-startup").start(() -> {
            try {
                compactor.compact(Instant.now());
            }
            catch (PersistenceException e) {
                logger.error("Startup compaction failed; the next scheduled run will pick it up", e);
            }
        });
    }
}
