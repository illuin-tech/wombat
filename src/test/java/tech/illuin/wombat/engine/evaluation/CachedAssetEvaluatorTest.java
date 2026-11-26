package tech.illuin.wombat.engine.evaluation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.illuin.wombat.core.activity.WombatActivityException;
import tech.illuin.wombat.core.activity.WombatActivityResolver;
import tech.illuin.wombat.core.activity.commons.ActivityData;
import tech.illuin.wombat.core.activity.commons.AssetActivity;
import tech.illuin.wombat.core.activity.commons.AssetFilter;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.evaluation.AssetEvaluation;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.evaluation.EvaluationReport;
import tech.illuin.wombat.core.evaluation.WombatEvaluationException;
import tech.illuin.wombat.core.evaluation.WombatEvaluationResolver;
import tech.illuin.wombat.core.evaluation.impact.commons.AssetImpact;
import tech.illuin.wombat.core.module.ProcessorFactory;
import tech.illuin.wombat.engine.evaluation.CachedAssetEvaluatorProperties.ResolverCacheProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CachedAssetEvaluatorTest
{
    private AssetEvaluator delegate;
    private CachedAssetEvaluatorProperties properties;
    private CachedAssetEvaluator evaluator;

    private TimeRange timeRange;
    private AssetFilter filter;
    private AssetIdentity identity;
    private Asset asset;

    @BeforeEach
    void setUp()
    {
        this.delegate = mock(AssetEvaluator.class);
        this.properties = new TestCachedAssetEvaluatorProperties(true, 1000L, Duration.ofMinutes(10), List.of());
        this.evaluator = new CachedAssetEvaluator(this.delegate, this.properties);

        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-01-02T00:00:00Z");
        this.timeRange = new TimeRange(start, end, Duration.ofHours(1));
        this.filter = new AssetFilter(Set.of(new AssetFilter.Environment("env1", Set.of(new AssetFilter.Asset("asset1", Set.of("serviceA"))))));

        this.identity = new AssetIdentity("asset1", "env1", "tech.illuin.wombat-module.kubernetes-api");
        this.asset = mock(Asset.class);
        when(this.asset.identity()).thenReturn(this.identity);
    }

    @Test
    void identicalEvaluateRequestsHitCacheAndInvokeDelegateOnce() throws WombatEvaluationException
    {
        AssetEvaluation eval1 = mock(AssetEvaluation.class);
        when(this.delegate.report(this.timeRange, this.filter)).thenReturn(new EvaluationReport(List.of(eval1), List.of()));

        List<AssetEvaluation> result1 = this.evaluator.report(this.timeRange, this.filter).evaluations();
        List<AssetEvaluation> result2 = this.evaluator.report(this.timeRange, this.filter).evaluations();

        assertEquals(1, result1.size());
        assertEquals(result1, result2);
        verify(this.delegate, times(1)).report(this.timeRange, this.filter);
    }

    @Test
    void differingTimeRangesTriggerEvaluateCacheMiss() throws WombatEvaluationException
    {
        AssetEvaluation eval1 = mock(AssetEvaluation.class);
        AssetEvaluation eval2 = mock(AssetEvaluation.class);

        TimeRange otherTimeRange = new TimeRange(
            this.timeRange.start(),
            this.timeRange.end().plus(Duration.ofDays(1)),
            this.timeRange.resolution()
        );

        when(this.delegate.report(this.timeRange, this.filter)).thenReturn(new EvaluationReport(List.of(eval1), List.of()));
        when(this.delegate.report(otherTimeRange, this.filter)).thenReturn(new EvaluationReport(List.of(eval2), List.of()));

        List<AssetEvaluation> result1 = this.evaluator.report(this.timeRange, this.filter).evaluations();
        List<AssetEvaluation> result2 = this.evaluator.report(otherTimeRange, this.filter).evaluations();

        assertEquals(List.of(eval1), result1);
        assertEquals(List.of(eval2), result2);
        verify(this.delegate, times(1)).report(this.timeRange, this.filter);
        verify(this.delegate, times(1)).report(otherTimeRange, this.filter);
    }

    @Test
    void differingAssetFiltersTriggerEvaluateCacheMiss() throws WombatEvaluationException
    {
        AssetEvaluation eval1 = mock(AssetEvaluation.class);
        AssetEvaluation eval2 = mock(AssetEvaluation.class);

        AssetFilter otherFilter = new AssetFilter(Set.of(new AssetFilter.Environment("env2", Set.of())));

        when(this.delegate.report(this.timeRange, this.filter)).thenReturn(new EvaluationReport(List.of(eval1), List.of()));
        when(this.delegate.report(this.timeRange, otherFilter)).thenReturn(new EvaluationReport(List.of(eval2), List.of()));

        List<AssetEvaluation> result1 = this.evaluator.report(this.timeRange, this.filter).evaluations();
        List<AssetEvaluation> result2 = this.evaluator.report(this.timeRange, otherFilter).evaluations();

        assertEquals(List.of(eval1), result1);
        assertEquals(List.of(eval2), result2);
        verify(this.delegate, times(1)).report(this.timeRange, this.filter);
        verify(this.delegate, times(1)).report(this.timeRange, otherFilter);
    }

    @Test
    void typedEvaluationSharesCacheEntryWithUntypedEvaluation() throws WombatEvaluationException
    {
        AssetImpact impact = mock(AssetImpact.class);
        AssetEvaluation otherEvaluation = mock(AssetEvaluation.class);

        when(this.delegate.report(this.timeRange, this.filter)).thenReturn(new EvaluationReport(List.of(impact, otherEvaluation), List.of()));

        List<AssetImpact> typedResults = this.evaluator.report(this.timeRange, this.filter).evaluations(AssetImpact.class);
        List<AssetEvaluation> untypedResults = this.evaluator.report(this.timeRange, this.filter).evaluations();

        assertEquals(List.of(impact), typedResults);
        assertEquals(List.of(impact, otherEvaluation), untypedResults);
        verify(this.delegate, times(1)).report(this.timeRange, this.filter);
    }

    @Test
    void evaluateExceptionPropagatesCleanlyWithoutPoisoningSubsequentCalls() throws WombatEvaluationException
    {
        when(this.delegate.report(this.timeRange, this.filter))
            .thenThrow(new WombatEvaluationException("Evaluation failed"))
            .thenReturn(new EvaluationReport(List.of(mock(AssetEvaluation.class)), List.of()));

        assertThrows(WombatEvaluationException.class, () -> this.evaluator.report(this.timeRange, this.filter).evaluations());

        List<AssetEvaluation> secondResult = this.evaluator.report(this.timeRange, this.filter).evaluations();
        assertEquals(1, secondResult.size());
        verify(this.delegate, times(2)).report(this.timeRange, this.filter);
    }

    @Test
    void identicalComputeActivityRequestsHitCache() throws WombatActivityException
    {
        AssetActivity activity = mock(AssetActivity.class);
        when(this.delegate.computeActivity(this.asset, this.timeRange, this.filter)).thenReturn(Optional.of(activity));

        Optional<AssetActivity> result1 = this.evaluator.computeActivity(this.asset, this.timeRange, this.filter);
        Optional<AssetActivity> result2 = this.evaluator.computeActivity(this.asset, this.timeRange, this.filter);

        assertEquals(Optional.of(activity), result1);
        assertEquals(Optional.of(activity), result2);
        verify(this.delegate, times(1)).computeActivity(this.asset, this.timeRange, this.filter);
    }

    @Test
    void differingComputeActivityParametersTriggerCacheMiss() throws WombatActivityException
    {
        AssetActivity act1 = mock(AssetActivity.class);
        AssetActivity act2 = mock(AssetActivity.class);

        Asset otherAsset = mock(Asset.class);
        when(otherAsset.identity()).thenReturn(new AssetIdentity("asset2", "env2", "tech.illuin.wombat-module.kubernetes-api"));

        when(this.delegate.computeActivity(this.asset, this.timeRange, this.filter)).thenReturn(Optional.of(act1));
        when(this.delegate.computeActivity(otherAsset, this.timeRange, this.filter)).thenReturn(Optional.of(act2));

        Optional<AssetActivity> result1 = this.evaluator.computeActivity(this.asset, this.timeRange, this.filter);
        Optional<AssetActivity> result2 = this.evaluator.computeActivity(otherAsset, this.timeRange, this.filter);

        assertEquals(Optional.of(act1), result1);
        assertEquals(Optional.of(act2), result2);
        verify(this.delegate, times(1)).computeActivity(this.asset, this.timeRange, this.filter);
        verify(this.delegate, times(1)).computeActivity(otherAsset, this.timeRange, this.filter);
    }

    @Test
    void computeActivityExceptionPropagatesCleanly() throws WombatActivityException
    {
        when(this.delegate.computeActivity(this.asset, this.timeRange, this.filter))
            .thenThrow(new WombatActivityException("Activity failed"))
            .thenReturn(Optional.of(mock(AssetActivity.class)));

        assertThrows(WombatActivityException.class, () -> this.evaluator.computeActivity(this.asset, this.timeRange, this.filter));

        Optional<AssetActivity> secondResult = this.evaluator.computeActivity(this.asset, this.timeRange, this.filter);
        assertTrue(secondResult.isPresent());
        verify(this.delegate, times(2)).computeActivity(this.asset, this.timeRange, this.filter);
    }

    @Test
    void identicalComputeImpactRequestsHitCache() throws WombatEvaluationException
    {
        ActivityData data = mock(ActivityData.class);
        AssetEvaluation eval = mock(AssetEvaluation.class);
        when(this.delegate.computeImpact(this.asset, data)).thenReturn(Optional.of(eval));

        Optional<AssetEvaluation> result1 = this.evaluator.computeImpact(this.asset, data);
        Optional<AssetEvaluation> result2 = this.evaluator.computeImpact(this.asset, data);

        assertEquals(Optional.of(eval), result1);
        assertEquals(Optional.of(eval), result2);
        verify(this.delegate, times(1)).computeImpact(this.asset, data);
    }

    @Test
    void differingComputeImpactParametersTriggerCacheMiss() throws WombatEvaluationException
    {
        ActivityData data1 = mock(ActivityData.class);
        ActivityData data2 = mock(ActivityData.class);
        AssetEvaluation eval1 = mock(AssetEvaluation.class);
        AssetEvaluation eval2 = mock(AssetEvaluation.class);

        when(this.delegate.computeImpact(this.asset, data1)).thenReturn(Optional.of(eval1));
        when(this.delegate.computeImpact(this.asset, data2)).thenReturn(Optional.of(eval2));

        Optional<AssetEvaluation> result1 = this.evaluator.computeImpact(this.asset, data1);
        Optional<AssetEvaluation> result2 = this.evaluator.computeImpact(this.asset, data2);

        assertEquals(Optional.of(eval1), result1);
        assertEquals(Optional.of(eval2), result2);
        verify(this.delegate, times(1)).computeImpact(this.asset, data1);
        verify(this.delegate, times(1)).computeImpact(this.asset, data2);
    }

    @Test
    void computeImpactExceptionPropagatesCleanly() throws WombatEvaluationException
    {
        ActivityData data = mock(ActivityData.class);
        when(this.delegate.computeImpact(this.asset, data))
            .thenThrow(new WombatEvaluationException("Impact failed"))
            .thenReturn(Optional.of(mock(AssetEvaluation.class)));

        assertThrows(WombatEvaluationException.class, () -> this.evaluator.computeImpact(this.asset, data));

        Optional<AssetEvaluation> secondResult = this.evaluator.computeImpact(this.asset, data);
        assertTrue(secondResult.isPresent());
        verify(this.delegate, times(2)).computeImpact(this.asset, data);
    }

    @Test
    void identicalComputeCostRequestsHitCache() throws WombatEvaluationException
    {
        ActivityData data = mock(ActivityData.class);
        AssetEvaluation eval = mock(AssetEvaluation.class);
        when(this.delegate.computeCost(this.asset, data)).thenReturn(Optional.of(eval));

        Optional<AssetEvaluation> result1 = this.evaluator.computeCost(this.asset, data);
        Optional<AssetEvaluation> result2 = this.evaluator.computeCost(this.asset, data);

        assertEquals(Optional.of(eval), result1);
        assertEquals(Optional.of(eval), result2);
        verify(this.delegate, times(1)).computeCost(this.asset, data);
    }

    @Test
    void differingComputeCostParametersTriggerCacheMiss() throws WombatEvaluationException
    {
        ActivityData data1 = mock(ActivityData.class);
        ActivityData data2 = mock(ActivityData.class);
        AssetEvaluation eval1 = mock(AssetEvaluation.class);
        AssetEvaluation eval2 = mock(AssetEvaluation.class);

        when(this.delegate.computeCost(this.asset, data1)).thenReturn(Optional.of(eval1));
        when(this.delegate.computeCost(this.asset, data2)).thenReturn(Optional.of(eval2));

        Optional<AssetEvaluation> result1 = this.evaluator.computeCost(this.asset, data1);
        Optional<AssetEvaluation> result2 = this.evaluator.computeCost(this.asset, data2);

        assertEquals(Optional.of(eval1), result1);
        assertEquals(Optional.of(eval2), result2);
        verify(this.delegate, times(1)).computeCost(this.asset, data1);
        verify(this.delegate, times(1)).computeCost(this.asset, data2);
    }

    @Test
    void computeCostExceptionPropagatesCleanly() throws WombatEvaluationException
    {
        ActivityData data = mock(ActivityData.class);
        when(this.delegate.computeCost(this.asset, data))
            .thenThrow(new WombatEvaluationException("Cost failed"))
            .thenReturn(Optional.of(mock(AssetEvaluation.class)));

        assertThrows(WombatEvaluationException.class, () -> this.evaluator.computeCost(this.asset, data));

        Optional<AssetEvaluation> secondResult = this.evaluator.computeCost(this.asset, data);
        assertTrue(secondResult.isPresent());
        verify(this.delegate, times(2)).computeCost(this.asset, data);
    }

    @Test
    void refreshForwardsToTheDelegateAndDropsTheAssetsCachedResults() throws Exception
    {
        AssetActivity activity = mock(AssetActivity.class);
        when(this.delegate.computeActivity(this.asset, this.timeRange, this.filter)).thenReturn(Optional.of(activity));
        when(this.delegate.report(this.timeRange, this.filter)).thenReturn(new EvaluationReport(List.of(mock(AssetEvaluation.class)), List.of()));

        this.evaluator.computeActivity(this.asset, this.timeRange, this.filter);
        this.evaluator.report(this.timeRange, this.filter).evaluations();
        this.evaluator.refresh(this.identity);
        this.evaluator.computeActivity(this.asset, this.timeRange, this.filter);
        this.evaluator.report(this.timeRange, this.filter).evaluations();

        verify(this.delegate).refresh(this.identity);
        verify(this.delegate, times(2)).computeActivity(this.asset, this.timeRange, this.filter);
        verify(this.delegate, times(2)).report(this.timeRange, this.filter);
    }

    @Test
    void onlyConfiguredActivityResolverModulesAreCached()
    {
        AssetType matchingType = mock(AssetType.class);
        when(matchingType.name()).thenReturn("configured-module");
        AssetType otherType = mock(AssetType.class);
        when(otherType.name()).thenReturn("other-module");
        ProcessorFactory<WombatActivityResolver> matchingFactory = (services, asset) -> mock(WombatActivityResolver.class);
        ProcessorFactory<WombatActivityResolver> otherFactory = (services, asset) -> mock(WombatActivityResolver.class);
        ResolverCacheProperties resolverCache = new TestResolverCacheProperties("configured-module", 100, Duration.ofMinutes(10));
        CachedAssetEvaluator evaluator = new CachedAssetEvaluator(
            this.delegate,
            new TestCachedAssetEvaluatorProperties(true, 1000L, Duration.ofMinutes(10), List.of(resolverCache))
        );

        evaluator.registerActivityResolver(matchingType, matchingFactory);
        evaluator.registerActivityResolver(otherType, otherFactory);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProcessorFactory<WombatActivityResolver>> factoryCaptor = ArgumentCaptor.forClass(ProcessorFactory.class);
        verify(this.delegate, times(2)).registerActivityResolver(org.mockito.ArgumentMatchers.any(), factoryCaptor.capture());
        assertInstanceOf(CachedActivityResolver.class, factoryCaptor.getAllValues().getFirst().create(null, this.asset));
        assertSame(otherFactory, factoryCaptor.getAllValues().getLast());
    }

    @Test
    void resolverRegistrationsAndCloseAreForwarded() throws Exception
    {
        AssetType assetType = mock(AssetType.class);
        ProcessorFactory<WombatActivityResolver> activityResolver = (services, asset) -> mock(WombatActivityResolver.class);
        ProcessorFactory<WombatEvaluationResolver> evaluationResolver = (services, asset) -> mock(WombatEvaluationResolver.class);

        assertSame(this.evaluator, this.evaluator.registerActivityResolver(assetType, activityResolver));
        assertSame(this.evaluator, this.evaluator.registerCostResolver(assetType, evaluationResolver));
        assertSame(this.evaluator, this.evaluator.registerImpactResolver(assetType, evaluationResolver));

        verify(this.delegate).registerActivityResolver(assetType, activityResolver);
        verify(this.delegate).registerCostResolver(assetType, evaluationResolver);
        verify(this.delegate).registerImpactResolver(assetType, evaluationResolver);

        AssetEvaluation eval = mock(AssetEvaluation.class);
        when(this.delegate.report(this.timeRange, this.filter)).thenReturn(new EvaluationReport(List.of(eval), List.of()));

        this.evaluator.report(this.timeRange, this.filter).evaluations();
        verify(this.delegate, times(1)).report(this.timeRange, this.filter);

        this.evaluator.close();
        verify(this.delegate).close();

        // After close, caches are invalidated so next evaluate invocation triggers delegate again
        this.evaluator.report(this.timeRange, this.filter).evaluations();
        verify(this.delegate, times(2)).report(this.timeRange, this.filter);
    }

    private record TestCachedAssetEvaluatorProperties(
        boolean enabled,
        long maximumSize,
        Duration expireAfterWrite,
        List<ResolverCacheProperties> activityResolverCaches
    ) implements CachedAssetEvaluatorProperties {}

    private record TestResolverCacheProperties(String module, long maximumSize, Duration expireAfterWrite) implements ResolverCacheProperties {}
}
