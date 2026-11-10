package tech.illuin.wombat.engine.module.library.llm_simulated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tech.illuin.wombat.core.activity.commons.Country;
import tech.illuin.wombat.core.asset.profile.LLMProfile;
import tech.illuin.wombat.core.asset.profile.LLMProvider;

public record LLMSimulatedProfile(
    @NotNull LLMProvider provider,
    @NotBlank String model,
    @NotNull Country location
) implements LLMProfile {}
