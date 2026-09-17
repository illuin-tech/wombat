package tech.illuin.wombat.persistence.micrometer;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.core.source.data.LLMData;
import tech.illuin.wombat.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.impact.llm.LLMModelMetricRepository;
import tech.illuin.wombat.persistence.micrometer.data.MetricGroup;
import tech.illuin.wombat.persistence.micrometer.data.TagGroup;
import tech.illuin.wombat.persistence.model.KubernetesMetricEntity;
import tech.illuin.wombat.persistence.model.LLMMetricEntity;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static tech.illuin.wombat.core.source.persistence.micrometer.MicrometerTags.*;

public class WombatStepMeterRegistry extends StepMeterRegistry
{
    private final KubernetesMetricRepository kubernetesRepository;
    private final LLMModelMetricRepository llmRepository;
    private final Clock clock;

    private static final Set<String> KUBERNETES_VALUES = Set.of(
        METRIC_K8S_CPU,
        METRIC_K8S_RAM
    );
    private static final Set<String> KUBERNETES_TAGS = Set.of(
        TAG_ENVIRONMENT,
        TAG_ASSET,
        TAG_SERVICE,
        TAG_K8S_CLUSTER,
        TAG_K8S_NAMESPACE,
        TAG_K8S_POD,
        TAG_K8S_CONTAINER
    );

    private static final Set<String> LLM_VALUES = Set.of(
        METRIC_LLM_OUTPUT_TOKENS
    );
    private static final Set<String> LLM_TAGS = Set.of(
        TAG_ENVIRONMENT,
        TAG_ASSET,
        TAG_SERVICE,
        TAG_LLM_MODEL
    );

    private static final Logger logger = LoggerFactory.getLogger(WombatStepMeterRegistry.class);

    public WombatStepMeterRegistry(
        StepRegistryConfig config,
        Clock clock,
        KubernetesMetricRepository kubernetesRepository,
        LLMModelMetricRepository llmRepository
    ) {
        super(config, clock);
        this.kubernetesRepository = kubernetesRepository;
        this.llmRepository = llmRepository;
        this.clock = clock;
    }

    @Override
    protected TimeUnit getBaseTimeUnit()
    {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    protected void publish()
    {
        logger.info("publish() invoked at wallTime={}", this.clock.wallTime());
        long ms = this.clock.wallTime();

        List<Meter> meters = this.getMeters();
        this.publishKubernetesData(ms, gather(meters, KUBERNETES_TAGS, KUBERNETES_VALUES));
        this.publishLLMData(ms, gather(meters, LLM_TAGS, LLM_VALUES));
    }

    private static Collection<MetricGroup> gather(List<Meter> meters, Set<String> tagKeys, Set<String> valueKeys)
    {
        Map<TagGroup, MetricGroup> groups = new HashMap<>();
        for (Meter meter : meters)
        {
            Meter.Id id = meter.getId();

            TagGroup.from(id, tagKeys).map(
                tags -> groups.computeIfAbsent(tags, MetricGroup::new)
            ).ifPresent(group -> group.recordValues(meter, valueKeys));
        }
        return groups.values();
    }

    private void publishKubernetesData(long ms, Collection<MetricGroup> groups)
    {
        int persisted = 0;
        for (MetricGroup group : groups)
        {
            Optional<Double> cpu = group.value(METRIC_K8S_CPU);
            Optional<Double> ram = group.value(METRIC_K8S_RAM);
            // Micrometer never unregisters a meter, so a container that stopped reporting still yields a group —
            // with tags but no sample in the window that just closed. Skip it instead of failing the whole batch.
            if (cpu.isEmpty() || ram.isEmpty())
            {
                logger.debug("No sample in the last window for container {}, skipping", group.tag(TAG_K8S_POD).orElse("<unknown>"));
                continue;
            }

            KubernetesData data = new KubernetesData(
                group.tag(TAG_SERVICE).orElseThrow(),
                group.tag(TAG_ASSET).orElseThrow(),
                group.tag(TAG_ENVIRONMENT).orElseThrow(),
                group.tag(TAG_K8S_CLUSTER).orElseThrow(),
                group.tag(TAG_K8S_NAMESPACE).orElseThrow(),
                group.tag(TAG_K8S_POD).orElseThrow(),
                cpu.get(),
                ram.get()
            );
            KubernetesMetricEntity row = new KubernetesMetricEntity();
            row.instantMs = ms;
            row.data = data;
            row.cpuNanocores = data.cpuNanocores();
            row.ramBytes = data.ramBytes();
            this.kubernetesRepository.save(row);
            logger.trace("Persisted row {}", row);
            persisted++;
        }

        if (persisted == 0)
        {
            logger.info("No CPU/RAM samples in the last window; skipping write");
            return;
        }
        logger.info("Persisted {} container row(s) at {}", persisted, ms);
    }

    private void publishLLMData(long ms, Collection<MetricGroup> groups)
    {
        int persisted = 0;
        for (MetricGroup group : groups)
        {
            Optional<Double> outputTokens = group.value(METRIC_LLM_OUTPUT_TOKENS);
            if (outputTokens.isEmpty())
            {
                logger.debug("No sample in the last window for model {}, skipping", group.tag(TAG_LLM_MODEL).orElse("<unknown>"));
                continue;
            }

            LLMData data = new LLMData(
                group.tag(TAG_SERVICE).orElseThrow(),
                group.tag(TAG_ASSET).orElseThrow(),
                group.tag(TAG_ENVIRONMENT).orElseThrow(),
                group.tag(TAG_LLM_MODEL).orElseThrow(),
                outputTokens.map(Double::longValue).get()
            );

            LLMMetricEntity row = new LLMMetricEntity();
            row.instantMs = ms;
            row.data = data;
            row.outputTokens = data.outputTokens();
            this.llmRepository.save(row);
            logger.trace("Persisted row {}", row);
            persisted++;
        }

        if (persisted == 0)
        {
            logger.info("No LLM samples in the last window; skipping write");
            return;
        }
        logger.info("Persisted {} llm-model row(s) at {}", persisted, ms);
    }

    public void flush()
    {
        this.publish();
    }
}
