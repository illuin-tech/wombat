package tech.illuin.wombat.persistence.store;

import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.source.data.MetricData;

public record MetricSample<T extends MetricData>(
    AssetIdentity asset,
    String assetType,
    long instantMs,
    long windowMs,
    T data
) {}
