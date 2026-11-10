package tech.illuin.wombat.engine.module.library.kubernetes_simulated;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import tech.illuin.wombat.core.activity.kubernetes.KubernetesActivityData;
import tech.illuin.wombat.core.asset.SimulatedAsset;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedProfile;

public record KubernetesSimulatedAsset(
    @NotNull @Valid KubernetesActivityData activity,
    @NotNull @JsonProperty("profile") KubernetesSimulatedProfile profile
) implements SimulatedAsset {
    public AssetType type()
    {
        return KubernetesSimulatedModule.TYPE;
    }
}
