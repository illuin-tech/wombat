package tech.illuin.wombat.engine.impact.timeline;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.activity.WombatActivityException;
import tech.illuin.wombat.core.activity.WombatActivityResolver;
import tech.illuin.wombat.core.activity.WombatActivityResolver.ActivityResolverType;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.asset.profile.AssetProfile;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.context.ResolvedContext;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.evaluation.impact.commons.AssetImpact;
import tech.illuin.wombat.core.evaluation.impact.commons.Footprint;
import tech.illuin.wombat.core.evaluation.impact.commons.ImpactProvider;
import tech.illuin.wombat.core.evaluation.impact.llm.LLMImpact;
import tech.illuin.wombat.engine.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.engine.impact.llm.LLMModelMetricRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static tech.illuin.wombat.core.activity.commons.Country.FRANCE;

class ImpactTimelineResolverTest
{
    private static final long HOUR_MS = 3_600_000L;
    private static final TimeRange RANGE = new TimeRange(Instant.ofEpochMilli(0), Instant.ofEpochMilli(2 * HOUR_MS));
    private static final AssetType EXTERNAL_TYPE = AssetType.of("tech.test", "llm-external", ActivityRegime.MEASURED, ServiceFamily.LLM);
    private static final AssetType INTERNAL_TYPE = AssetType.of("tech.test", "llm-internal", ActivityRegime.MEASURED, ServiceFamily.LLM);

    private final KubernetesMetricRepository kubernetesRepository = mock(KubernetesMetricRepository.class);
    private final LLMModelMetricRepository llmRepository = mock(LLMModelMetricRepository.class);
    private final WombatActivityResolver externalResolver = mock(WombatActivityResolver.class);
    private final WombatActivityResolver internalResolver = (asset, range, filter) -> Optional.empty();
    private final TestAsset externalAsset = new TestAsset(AssetIdentity.of("external", "env", "External"), EXTERNAL_TYPE);
    private final TestAsset internalAsset = new TestAsset(AssetIdentity.of("internal", "env", "Internal"), INTERNAL_TYPE);
    private final AssetEvaluator evaluator = new AssetEvaluator(() -> new ResolvedContext(new Environment("env", List.of(this.externalAsset, this.internalAsset))))
        .registerActivityResolver(EXTERNAL_TYPE, this.externalResolver)
        .registerActivityResolver(INTERNAL_TYPE, this.internalResolver);
    private final ImpactTimelineResolver resolver = new ImpactTimelineResolver(this.kubernetesRepository, this.llmRepository, this.evaluator);

    @AfterEach
    void close() throws Exception
    {
        this.evaluator.close();
    }

    @Test
    void externalAssets_takeTheirActivityFromTheirResolver() throws WombatActivityException
    {
        when(this.externalResolver.activityResolverType()).thenReturn(ActivityResolverType.EXTERNAL_API);
        when(this.externalResolver.activityPerBucket(eq(this.externalAsset), eq(RANGE), eq(HOUR_MS), eq(List.of("claude"))))
            .thenReturn(Map.of(HOUR_MS, 1.0));

        ImpactTimeline timeline = this.resolver.resolve(RANGE, List.of(impact("external", EXTERNAL_TYPE, "claude", 10.0f)), Set.of("claude"));

        List<ImpactTimeline.Bucket> buckets = gwp(timeline).buckets();
        assertEquals(0.0, buckets.get(0).use(), 1e-6);
        assertEquals(10.0, buckets.get(1).use(), 1e-6);
        verifyNoInteractions(this.llmRepository);
    }

    @Test
    void internalAssets_keepReadingTheMetricsStore()
    {
        when(this.llmRepository.outputTokensPerBucket(anyLong(), anyLong(), anyLong(), anyString(), anyCollection()))
            .thenReturn(Map.of(0L, 1.0));

        ImpactTimeline timeline = this.resolver.resolve(RANGE, List.of(impact("internal", INTERNAL_TYPE, "mistral", 10.0f)), Set.of("mistral"));

        assertEquals(10.0, gwp(timeline).buckets().get(0).use(), 1e-6);
        verify(this.llmRepository).outputTokensPerBucket(0L, 2 * HOUR_MS, HOUR_MS, "internal", List.of("mistral"));
    }

    @Test
    void externalFailures_fallBackToAnEvenSpread() throws WombatActivityException
    {
        when(this.externalResolver.activityResolverType()).thenReturn(ActivityResolverType.EXTERNAL_API);
        when(this.externalResolver.activityPerBucket(any(), any(), anyLong(), anyCollection()))
            .thenThrow(new WombatActivityException("unavailable"));

        ImpactTimeline timeline = this.resolver.resolve(RANGE, List.of(impact("external", EXTERNAL_TYPE, "claude", 10.0f)), Set.of("claude"));

        gwp(timeline).buckets().forEach(bucket -> assertEquals(5.0, bucket.use(), 1e-6));
    }

    private static ImpactTimeline.Series gwp(ImpactTimeline timeline)
    {
        return timeline.series().stream().filter(series -> series.key().equals("gwp")).findFirst().orElseThrow();
    }

    private static AssetImpact impact(String assetId, AssetType type, String serviceId, float use)
    {
        Footprint footprint = new Footprint(
            new Footprint.FootprintImpact(
                "kgCO2eq", "",
                new Footprint.FootprintImpact.FootprintImpactItem(0.0f, List.of()),
                new Footprint.FootprintImpact.FootprintImpactItem(use, List.of())
            ),
            null,
            null
        );
        LLMImpact service = new LLMImpact(serviceId, type, null, LLMProvider.anthropic, serviceId, FRANCE, footprint, null, 100L, 1.0);
        return new AssetImpact("env", assetId, footprint, List.of(service), ImpactProvider.ECOLOGITS);
    }

    private record TestAsset(AssetIdentity identity, AssetType type) implements Asset
    {
        @Override
        public AssetProfile profile()
        {
            return null;
        }
    }
}
