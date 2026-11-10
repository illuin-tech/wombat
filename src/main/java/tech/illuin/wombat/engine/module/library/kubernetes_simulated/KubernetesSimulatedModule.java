package tech.illuin.wombat.engine.module.library.kubernetes_simulated;

import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.module.WombatModule;

public class KubernetesSimulatedModule implements WombatModule
{
    public static final AssetType TYPE = AssetType.of("tech.illuin", "wombat-module", "kubernetes-simulated", ActivityRegime.MODELED, ServiceFamily.KUBERNETES_CONTAINER);

    @Override
    public AssetType type()
    {
        return TYPE;
    }

    @Override
    public Class<? extends Asset> assetClass()
    {
        return KubernetesSimulatedAsset.class;
    }
}
