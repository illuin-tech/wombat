package tech.illuin.wombat.engine.module.library.kubernetes_simulated;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tech.illuin.wombat.core.activity.commons.Country;
import tech.illuin.wombat.core.asset.profile.Lifetime;
import tech.illuin.wombat.core.asset.profile.ServerProfile;
import tech.illuin.wombat.core.asset.profile.ServerProvider;

public record KubernetesSimulatedProfile(
    @NotNull @JsonProperty("provider") ServerProvider provider,
    @NotBlank @JsonProperty("instanceType") @JsonAlias("instance-type") String instanceType,
    @NotNull @JsonProperty("location") Country location,
    @NotNull @JsonProperty("lifespan") Lifetime lifespan,
    @Positive @JsonProperty("nodeCount") @JsonAlias("node-count") int nodeCount
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
