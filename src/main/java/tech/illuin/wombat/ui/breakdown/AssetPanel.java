package tech.illuin.wombat.ui.breakdown;

import tech.illuin.wombat.core.evaluation.impact.commons.Amount;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;

import java.util.List;

public interface AssetPanel
{
    String name();

    AssetType type();

    ActivityRegime regime();

    ServiceFamily family();

    List<String> serviceIds();

    Amount gwpTotal();

    Amount energyTotal();

    Amount peTotal();

    Amount adpeTotal();
}
