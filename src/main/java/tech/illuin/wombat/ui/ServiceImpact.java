package tech.illuin.wombat.ui;

import tech.illuin.wombat.core.asset.AssetType;
import tech.illuin.wombat.core.evaluation.impact.commons.Footprint;

public record ServiceImpact(
    String assetName,
    String service,
    String label,
    AssetType assetType,
    Footprint footprint
) {
    public static ServiceImpact of(String assetName, tech.illuin.wombat.core.evaluation.impact.commons.ServiceImpact impact)
    {
        return new ServiceImpact(
            assetName,
            impact.serviceId(),
            assetName + " / " + impact.serviceId(),
            impact.assetType(),
            impact.footprint()
        );
    }
}
