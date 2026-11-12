package tech.illuin.wombat.engine.module.registry;

import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.type.AssetType;

public record ModuleMetadata(
    AssetType type,
    Class<? extends Asset> assetClass,
    boolean enabled
) {}
