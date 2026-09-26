package tech.illuin.wombat.persistence.micrometer;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.illuin.wombat.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.impact.llm.LLMModelMetricRepository;
import tech.illuin.wombat.persistence.model.KubernetesMetricEntity;
import tech.illuin.wombat.persistence.model.LLMMetricEntity;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static tech.illuin.wombat.core.source.persistence.micrometer.MicrometerTags.*;

/**
 * Covers what a persisted row means: the instant it is stamped with, the window length it stands
 * for, and how the samples taken during that window collapse into its value.
 */
class WombatStepMeterRegistryTest
{
    private static final long HOUR_MS = 3_600_000L;

    private MockClock clock;
    private KubernetesMetricRepository kubernetesRepository;
    private LLMModelMetricRepository llmRepository;
    private WombatStepMeterRegistry registry;

    @BeforeEach
    void setUp()
    {
        this.clock = new MockClock();
        this.kubernetesRepository = mock(KubernetesMetricRepository.class);
        this.llmRepository = mock(LLMModelMetricRepository.class);
        this.registry = new WombatStepMeterRegistry(hourlyConfig(), this.clock, this.kubernetesRepository, this.llmRepository);
    }

    @AfterEach
    void tearDown()
    {
        this.registry.close();
    }

    @Test
    void publish_averagesTheCpuSamplesOfTheWindow()
    {
        this.recordCpu(100.0);
        this.recordCpu(300.0);
        this.recordRam(2048.0);

        this.closeWindow();

        KubernetesMetricEntity row = this.captureKubernetesRow();
        assertEquals(200.0, row.cpuNanocores, 1e-9);
        assertEquals(2048.0, row.ramBytes, 1e-9);
    }

    /** Each sample is the tokens produced since the previous one, so the window holds their total. */
    @Test
    void publish_sumsTheOutputTokensOfTheWindow()
    {
        this.recordTokens(120.0);
        this.recordTokens(80.0);

        this.closeWindow();

        ArgumentCaptor<LLMMetricEntity> captor = ArgumentCaptor.forClass(LLMMetricEntity.class);
        verify(this.llmRepository).save(captor.capture());
        assertEquals(200L, captor.getValue().outputTokens);
    }

    @Test
    void publish_stampsTheRowWithTheStartOfTheWindowItSummarises()
    {
        this.recordCpu(100.0);
        this.recordRam(1.0);

        this.closeWindow();

        KubernetesMetricEntity row = this.captureKubernetesRow();
        assertEquals(0L, row.instantMs);
        assertEquals(HOUR_MS, row.windowMs);
    }

    /**
     * Micrometer schedules the publish at a random offset of up to 80% of the step past the window
     * boundary, so the clock at publish time says nothing about which window the samples belong to.
     */
    @Test
    void publish_ignoresHowLateInTheNextWindowItRuns()
    {
        this.closeWindow();

        this.recordCpu(500.0);
        this.recordRam(1.0);
        this.clock.add(Duration.ofHours(1).plusMinutes(47));
        this.registry.flush();

        KubernetesMetricEntity row = this.captureKubernetesRow();
        assertEquals(HOUR_MS, row.instantMs);
        assertEquals(500.0, row.cpuNanocores, 1e-9);
    }

    /**
     * On shutdown micrometer flushes the window that closed and then, after rolling the meters over,
     * the partial one in progress — which belongs to a window the next start writes itself.
     */
    @Test
    void publish_writesAWindowOnlyOnce()
    {
        this.recordCpu(100.0);
        this.recordRam(1.0);

        this.closeWindow();
        this.registry.flush();
        this.clock.add(Duration.ofMinutes(30));
        this.registry.flush();

        verify(this.kubernetesRepository, times(1)).save(any());
    }

    @Test
    void publish_writesNothingForAWindowWithoutSamples()
    {
        this.closeWindow();

        verify(this.kubernetesRepository, never()).save(any());
        verify(this.llmRepository, never()).save(any());
    }

    /** Lets the window holding the recorded samples close, then drains it. */
    private void closeWindow()
    {
        this.clock.add(Duration.ofHours(1));
        this.registry.flush();
    }

    private KubernetesMetricEntity captureKubernetesRow()
    {
        ArgumentCaptor<KubernetesMetricEntity> captor = ArgumentCaptor.forClass(KubernetesMetricEntity.class);
        verify(this.kubernetesRepository).save(captor.capture());
        return captor.getValue();
    }

    private void recordCpu(double nanocores)
    {
        this.summary(METRIC_K8S_CPU, kubernetesTags()).record(nanocores);
    }

    private void recordRam(double bytes)
    {
        this.summary(METRIC_K8S_RAM, kubernetesTags()).record(bytes);
    }

    private void recordTokens(double tokens)
    {
        this.summary(METRIC_LLM_OUTPUT_TOKENS, llmTags()).record(tokens);
    }

    private DistributionSummary summary(String name, List<Tag> tags)
    {
        return DistributionSummary.builder(name).tags(tags).register(this.registry);
    }

    private static List<Tag> kubernetesTags()
    {
        return List.of(
            Tag.of(TAG_ENVIRONMENT, "env"),
            Tag.of(TAG_ASSET, "asset"),
            Tag.of(TAG_SERVICE, "api"),
            Tag.of(TAG_K8S_CLUSTER, "cluster"),
            Tag.of(TAG_K8S_NAMESPACE, "namespace"),
            Tag.of(TAG_K8S_POD, "pod"),
            Tag.of(TAG_K8S_CONTAINER, "api")
        );
    }

    private static List<Tag> llmTags()
    {
        return List.of(
            Tag.of(TAG_ENVIRONMENT, "env"),
            Tag.of(TAG_ASSET, "asset"),
            Tag.of(TAG_SERVICE, "mistral-large-latest"),
            Tag.of(TAG_LLM_MODEL, "mistral-large-latest")
        );
    }

    /** Disabled so the registry never schedules a publish of its own: every test drains it by hand. */
    private static StepRegistryConfig hourlyConfig()
    {
        return new StepRegistryConfig()
        {
            @Override
            public String prefix()
            {
                return "wombat";
            }

            @Override
            public Duration step()
            {
                return Duration.ofHours(1);
            }

            @Override
            public boolean enabled()
            {
                return false;
            }

            @Override
            public String get(@NonNull String key)
            {
                return null;
            }
        };
    }
}
