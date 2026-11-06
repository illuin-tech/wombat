package tech.illuin.wombat.persistence.backend.api.store;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.source.data.MetricData;

import java.time.Duration;
import java.time.Instant;

public record MetricSample<T extends MetricData>(
    @NotNull @Valid AssetIdentity asset,
    @NotNull @Valid TimeRange window,
    @NotNull @Valid T data
) {
    public static <T extends MetricData> MetricSample<T> of(AssetIdentity asset, long windowStartMs, long windowMs, T data)
    {
        Instant start = Instant.ofEpochMilli(windowStartMs);
        return new MetricSample<>(asset, TimeRange.between(start, start.plusMillis(windowMs)), data);
    }

    public long instantMs()
    {
        return this.window.start().toEpochMilli();
    }

    public long windowMs()
    {
        return Duration.between(this.window.start(), this.window.end()).toMillis();
    }
}
