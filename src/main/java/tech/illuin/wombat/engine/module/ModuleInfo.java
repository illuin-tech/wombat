package tech.illuin.wombat.engine.module;

import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata.Source;

/**
 * A module as the API serves it: its registry state ({@link tech.illuin.wombat.engine.module.registry.ModuleMetadata}),
 * and whether the running core holds it.
 *
 * @param type       as the module advertised it when last detected, for a missing one
 * @param assetClass null when the serving instance does not detect the module
 * @param enabled    the registry state, which the asset pickers follow at once
 * @param deployed   whether the core running on the serving instance holds the module
 * @param pending    whether the latest deployment does not hold the registry state yet, until a redeploy; never for a
 *                   missing module, which a redeploy cannot bring in
 * @param missing    whether no live instance detects the module any more, e.g. its extension jar was removed
 * @param source     null when the serving instance does not detect the module
 * @param assetCount the number of assets of this type, soft-deleted ones aside
 */
public record ModuleInfo(
    AssetType type,
    Class<? extends Asset> assetClass,
    boolean enabled,
    boolean creatable,
    boolean deployed,
    boolean pending,
    boolean missing,
    Source source,
    long assetCount
) {}
