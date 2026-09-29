package tech.illuin.wombat.ui.breakdown;

import tech.illuin.wombat.core.evaluation.impact.commons.Amount;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.asset.type.ServiceFamily;

import java.util.List;

public record LLMAssetPanel(
    String name,
    AssetType type,
    ActivityRegime regime,
    List<String> serviceIds,
    Amount gwpTotal,
    Amount energyTotal,
    Amount peTotal,
    Amount adpeTotal,
    Amount gwpPerRequest,
    LLMProvider provider,
    String model,
    String location,
    long outputTokenCount,
    int requestPerYear,
    double requestCount
) implements AssetPanel {
    public ServiceFamily family()
    {
        return ServiceFamily.LLM;
    }
}
