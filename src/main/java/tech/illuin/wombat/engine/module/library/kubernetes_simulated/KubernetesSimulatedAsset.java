package tech.illuin.wombat.engine.module.library.kubernetes_simulated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import tech.illuin.wombat.core.activity.kubernetes.KubernetesActivityData;
import tech.illuin.wombat.core.asset.SimulatedAsset;
import tech.illuin.wombat.core.asset.type.AssetType;

public record KubernetesSimulatedAsset(
    @NotNull @Valid KubernetesActivityData activity,
    @NotNull KubernetesSimulatedProfile profile
) implements SimulatedAsset {
    public AssetType type()
    {
        return KubernetesSimulatedModule.TYPE;
    }
}
