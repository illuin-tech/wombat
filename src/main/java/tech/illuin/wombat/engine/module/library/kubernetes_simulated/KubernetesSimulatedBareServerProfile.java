package tech.illuin.wombat.engine.module.library.kubernetes_simulated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tech.illuin.wombat.core.activity.commons.Country;
import tech.illuin.wombat.core.asset.profile.BareServerProfile;
import tech.illuin.wombat.core.asset.profile.Lifetime;

public record KubernetesSimulatedBareServerProfile(
    @NotNull Country location,
    @NotNull Lifetime lifespan,
    @Positive int nodeCount,
    @NotNull @Valid CPU cpu,
    @NotNull @Valid RAM ram,
    @Valid Disk ssd,
    @Valid Disk hdd,
    @Valid GPU gpu
) implements KubernetesSimulatedProfile, BareServerProfile
{
    public KubernetesSimulatedBareServerProfile
    {
        if (nodeCount <= 0)
            nodeCount = 1;
    }
}
