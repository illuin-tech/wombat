package tech.illuin.wombat.ui;

import tech.illuin.wombat.core.asset.AssetType;
import tech.illuin.wombat.core.evaluation.impact.commons.Footprint;

public record ServiceImpact(
    String assetName,
    String service,
    String label,
    boolean container,
    boolean llm,
    Footprint footprint
)
{
    public static ServiceImpact of(String assetName, tech.illuin.wombat.core.evaluation.impact.commons.ServiceImpact impact)
    {
        AssetType type = impact.assetType();
        return new ServiceImpact(
            assetName,
            impact.serviceId(),
            assetName + " / " + impact.serviceId(),
            type == AssetType.KUBERNETES_API,
            type == AssetType.LLM_STATIC || type == AssetType.LLM_PROMETHEUS,
            impact.footprint()
        );
    }
}
