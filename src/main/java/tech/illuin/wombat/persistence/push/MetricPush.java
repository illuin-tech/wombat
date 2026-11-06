package tech.illuin.wombat.persistence.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.source.data.MetricData;

import java.util.List;

public record MetricPush(
    @NotNull @Valid AssetIdentity asset,
    @NotBlank String assetType,
    @NotNull List<@NotNull MetricData> metrics
) {}
