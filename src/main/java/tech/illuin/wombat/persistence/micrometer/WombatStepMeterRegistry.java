package tech.illuin.wombat.persistence.micrometer;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.core.source.data.LLMData;
import tech.illuin.wombat.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.impact.llm.LLMModelMetricRepository;
import tech.illuin.wombat.persistence.micrometer.data.Aggregation;
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
    private final long stepMs;
    private long lastPublishedBucketMs;

    private static final Map<String, Aggregation> KUBERNETES_VALUES = Map.of(
        METRIC_K8S_CPU, Aggregation.MEAN,
        METRIC_K8S_RAM, Aggregation.MEAN
    );
    private static final Set<String> KUBERNETES_TAGS = Set.of(
        TAG_ENVIRONMENT,
        TAG_ASSET,
        TAG_ASSET_NAME,
        TAG_ASSET_TYPE,
        TAG_SERVICE,
        TAG_K8S_CLUSTER,
        TAG_K8S_NAMESPACE,
        TAG_K8S_POD,
        TAG_K8S_CONTAINER
    );

    private static final Map<String, Aggregation> LLM_VALUES = Map.of(
        METRIC_LLM_OUTPUT_TOKENS, Aggregation.SUM
    );
    private static final Set<String> LLM_TAGS = Set.of(
        TAG_ENVIRONMENT,
        TAG_ASSET,
        TAG_ASSET_NAME,
        TAG_ASSET_TYPE,
        TAG_SERVICE,
        TAG_LLM_PROVIDER,
        TAG_LLM_MODEL,
        TAG_LLM_LOCATION
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
        this.stepMs = config.step().toMillis();
        this.lastPublishedBucketMs = Long.MIN_VALUE;
    }

    @Override
    protected TimeUnit getBaseTimeUnit()
    {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    protected void publish()
    {
        long bucketMs = this.closedBucketStart();
        if (bucketMs <= this.lastPublishedBucketMs)
        {
            logger.debug("Window {} was already published, skipping (wallTime={})", bucketMs, this.clock.wallTime());
            return;
        }
        this.lastPublishedBucketMs = bucketMs;
        logger.info("publish() invoked at wallTime={} for window starting at {}", this.clock.wallTime(), bucketMs);

        List<Meter> meters = this.getMeters();
        this.publishKubernetesData(bucketMs, gather(meters, KUBERNETES_TAGS, KUBERNETES_VALUES));
        this.publishLLMData(bucketMs, gather(meters, LLM_TAGS, LLM_VALUES));
    }

    private long closedBucketStart()
    {
        return (this.clock.wallTime() / this.stepMs - 1) * this.stepMs;
    }

    private static Collection<MetricGroup> gather(List<Meter> meters, Set<String> tagKeys, Map<String, Aggregation> valueKeys)
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
            if (cpu.isEmpty() || ram.isEmpty())
            {
                logger.debug("No sample in the last window for container {}, skipping", group.tag(TAG_K8S_POD).orElse("<unknown>"));
                continue;
            }

            KubernetesData data = new KubernetesData(
                group.tag(TAG_SERVICE).orElseThrow(),
                group.tag(TAG_K8S_CLUSTER).orElseThrow(),
                group.tag(TAG_K8S_NAMESPACE).orElseThrow(),
                group.tag(TAG_K8S_POD).orElseThrow(),
                cpu.get(),
                ram.get()
            );
            KubernetesMetricEntity row = new KubernetesMetricEntity();
            row.instantMs = ms;
            row.windowMs = this.stepMs;
            row.assign(identityOf(group));
            row.assetType = group.tag(TAG_ASSET_TYPE).orElse(null);
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
                group.tag(TAG_LLM_PROVIDER).map(LLMProvider::valueOf).orElseThrow(),
                group.tag(TAG_LLM_MODEL).orElseThrow(),
                group.tag(TAG_LLM_LOCATION).orElseThrow(),
                outputTokens.map(Double::longValue).get()
            );

            LLMMetricEntity row = new LLMMetricEntity();
            row.instantMs = ms;
            row.assign(identityOf(group));
            row.assetType = group.tag(TAG_ASSET_TYPE).orElse(null);
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

    private static AssetIdentity identityOf(MetricGroup group)
    {
        return new AssetIdentity(
            group.tag(TAG_ASSET).orElseThrow(),
            group.tag(TAG_ENVIRONMENT).orElseThrow(),
            group.tag(TAG_ASSET_NAME).orElseThrow()
        );
    }

    public void flush()
    {
        this.publish();
    }
}
