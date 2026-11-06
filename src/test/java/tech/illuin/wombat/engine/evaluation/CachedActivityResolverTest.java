package tech.illuin.wombat.engine.evaluation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.activity.WombatActivityException;
import tech.illuin.wombat.core.activity.WombatActivityResolver;
import tech.illuin.wombat.core.activity.commons.AssetActivity;
import tech.illuin.wombat.core.activity.commons.AssetFilter;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.engine.module.extension.WombatModuleProperties.CacheProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CachedActivityResolverTest
{
    private WombatActivityResolver delegate;
    private CachedActivityResolver resolver;

    private TimeRange timeRange;
    private Asset asset;
    private Optional<AssetActivity> activity;

    @BeforeEach
    void setUp()
    {
        this.delegate = mock(WombatActivityResolver.class);
        this.resolver = new CachedActivityResolver(this.delegate, new TestCacheProperties());

        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        this.timeRange = new TimeRange(start, start.plus(Duration.ofDays(1)), Duration.ofHours(1));

        AssetIdentity identity = new AssetIdentity("anthropic.org", "env1", "Anthropic");
        this.asset = mock(Asset.class);
        when(this.asset.identity()).thenReturn(identity);

        this.activity = Optional.of(new AssetActivity(identity, List.of()));
    }

    @Test
    void identicalRequestsInvokeDelegateOnce() throws WombatActivityException
    {
        AssetFilter filter = AssetFilter.none();
        when(this.delegate.resolve(this.asset, this.timeRange, filter)).thenReturn(this.activity);

        assertSame(this.activity, this.resolver.resolve(this.asset, this.timeRange, filter));
        assertSame(this.activity, this.resolver.resolve(this.asset, this.timeRange, filter));
        verify(this.delegate, times(1)).resolve(this.asset, this.timeRange, filter);
    }

    @Test
    void filtersScopingOtherAssetsShareTheSameEntry() throws WombatActivityException
    {
        AssetFilter otherEnvironment = AssetFilter.of("env2", List.of("other"));
        when(this.delegate.resolve(any(), any(), any())).thenReturn(this.activity);

        this.resolver.resolve(this.asset, this.timeRange, AssetFilter.none());
        this.resolver.resolve(this.asset, this.timeRange, otherEnvironment);
        this.resolver.resolve(this.asset, this.timeRange, null);

        verify(this.delegate, times(1)).resolve(any(), any(), any());
    }

    @Test
    void differingServiceIdsOrRangesMissTheCache() throws WombatActivityException
    {
        AssetFilter modelFilter = new AssetFilter(Set.of(new AssetFilter.Environment("env1", Set.of(new AssetFilter.Asset("anthropic.org", Set.of("claude-opus-5-5"))))));
        TimeRange otherRange = new TimeRange(this.timeRange.start(), this.timeRange.end(), Duration.ofDays(1));
        when(this.delegate.resolve(any(), any(), any())).thenReturn(this.activity);

        this.resolver.resolve(this.asset, this.timeRange, AssetFilter.none());
        this.resolver.resolve(this.asset, this.timeRange, modelFilter);
        this.resolver.resolve(this.asset, otherRange, AssetFilter.none());

        verify(this.delegate, times(3)).resolve(any(), any(), any());
    }

    @Test
    void failuresAreNotCached() throws WombatActivityException
    {
        AssetFilter filter = AssetFilter.none();
        when(this.delegate.resolve(this.asset, this.timeRange, filter))
            .thenThrow(new WombatActivityException("rate limited"))
            .thenReturn(this.activity);

        assertThrows(WombatActivityException.class, () -> this.resolver.resolve(this.asset, this.timeRange, filter));
        assertEquals(this.activity, this.resolver.resolve(this.asset, this.timeRange, filter));
        verify(this.delegate, times(2)).resolve(this.asset, this.timeRange, filter);
    }

    private record TestCacheProperties() implements CacheProperties
    {
        @Override
        public boolean enabled()
        {
            return true;
        }

        @Override
        public long maximumSize()
        {
            return 100;
        }

        @Override
        public Duration expireAfterWrite()
        {
            return Duration.ofMinutes(30);
        }
    }
}
