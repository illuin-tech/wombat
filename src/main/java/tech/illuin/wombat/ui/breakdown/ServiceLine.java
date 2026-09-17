package tech.illuin.wombat.ui.breakdown;

import tech.illuin.wombat.core.evaluation.impact.commons.Amount;
import tech.illuin.wombat.core.asset.ActivityRegime;
import tech.illuin.wombat.core.asset.ServiceFamily;

public record ServiceLine(
    String name,
    ActivityRegime regime,
    ServiceFamily family,
    Amount gwpTotal,
    Amount energyTotal,
    Amount peTotal,
    Amount adpeTotal
) {}
