package tech.illuin.wombat.engine.module.library.llm_simulated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import tech.illuin.wombat.core.activity.llm.LLMActivityData;
import tech.illuin.wombat.core.asset.SimulatedAsset;
import tech.illuin.wombat.core.asset.type.AssetType;

public record LLMSimulatedAsset(
    @NotNull @Valid LLMActivityData activity,
    @NotNull LLMSimulatedProfile profile
) implements SimulatedAsset {
    public AssetType type()
    {
        return LLMSimulatedModule.TYPE;
    }
}
