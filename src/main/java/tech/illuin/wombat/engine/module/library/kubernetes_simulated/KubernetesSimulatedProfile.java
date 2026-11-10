package tech.illuin.wombat.engine.module.library.kubernetes_simulated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tech.illuin.wombat.core.activity.commons.Country;
import tech.illuin.wombat.core.asset.profile.Lifetime;
import tech.illuin.wombat.core.asset.profile.ServerProfile;
import tech.illuin.wombat.core.asset.profile.ServerProvider;

public record KubernetesSimulatedProfile(
    @NotNull ServerProvider provider,
    @NotBlank String instanceType,
    @NotNull Country location,
    @NotNull Lifetime lifespan,
    @Positive int nodeCount
) implements ServerProfile
{
    public KubernetesSimulatedProfile
    {
        if (nodeCount <= 0)
            nodeCount = 1;
    }

    public KubernetesSimulatedProfile(
        ServerProvider provider,
        String instanceType,
        Country location,
        Lifetime lifespan
    ) {
        this(provider, instanceType, location, lifespan, 1);
    }
}
