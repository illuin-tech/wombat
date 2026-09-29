package tech.illuin.wombat.ui.breakdown;

import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.asset.profile.ServerProvider;
import tech.illuin.wombat.core.evaluation.impact.commons.Amount;
import tech.illuin.wombat.core.evaluation.impact.kubernetes.ClusterInfo;
import tech.illuin.wombat.core.evaluation.impact.kubernetes.ServerSpecification;

import java.util.List;

public record KubernetesAssetPanel(
    String name,
    AssetType type,
    ActivityRegime regime,
    List<String> serviceIds,
    Amount gwpTotal,
    Amount energyTotal,
    Amount peTotal,
    Amount adpeTotal,
    ServerProvider provider,
    String instanceType,
    String location,
    int lifespan,
    List<ClusterInfo> clusters,
    ServerSpecification specification,
    Load load
) implements AssetPanel {
    public ServiceFamily family()
    {
        return ServiceFamily.KUBERNETES_CONTAINER;
    }

    public record Load(double cpuUsageCores, double loadPercent, int nodeCount) {}
}
