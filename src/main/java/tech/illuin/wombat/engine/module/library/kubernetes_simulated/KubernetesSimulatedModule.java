package tech.illuin.wombat.engine.module.library.kubernetes_simulated;

import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.module.WombatModule;

import java.util.Optional;

public class KubernetesSimulatedModule implements WombatModule
{
    public static final AssetType TYPE = AssetType.of("tech.illuin", "wombat-module", "kubernetes-simulated", ActivityRegime.MODELED, ServiceFamily.KUBERNETES_CONTAINER);

    @Override
    public AssetType type()
    {
        return TYPE;
    }

    @Override
    public Optional<Class<? extends Asset>> assetClass()
    {
        return Optional.of(KubernetesSimulatedAsset.class);
    }
}
