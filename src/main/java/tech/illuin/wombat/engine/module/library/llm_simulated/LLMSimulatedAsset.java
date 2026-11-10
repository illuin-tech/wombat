package tech.illuin.wombat.engine.module.library.llm_simulated;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import tech.illuin.wombat.core.activity.llm.LLMActivityData;
import tech.illuin.wombat.core.asset.SimulatedAsset;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedProfile;

public record LLMSimulatedAsset(
    @NotNull @Valid LLMActivityData activity,
    @NotNull @JsonProperty("profile") LLMSimulatedProfile profile
) implements SimulatedAsset {
    public AssetType type()
    {
        return LLMSimulatedModule.TYPE;
    }
}
