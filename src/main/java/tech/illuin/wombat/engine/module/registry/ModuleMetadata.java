package tech.illuin.wombat.engine.module.registry;

import com.fasterxml.jackson.annotation.JsonProperty;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.SimulatedAsset;
import tech.illuin.wombat.core.asset.type.AssetType;

/**
 * A registered module, as the registry knows it.
 *
 * @param type       for a missing module, as it advertised it when last detected
 * @param assetClass null when the module is missing
 * @param enabled    the registry state, shared by every instance
 * @param missing    whether the module is registered but no longer detected, e.g. its extension jar was removed
 * @param source     whether it comes from the extension folder; null when the module is missing
 */
public record ModuleMetadata(
    AssetType type,
    Class<? extends Asset> assetClass,
    boolean enabled,
    boolean missing,
    Source source
) {
    /**
     * A module detected on this instance, not from the extension folder.
     */
    public static ModuleMetadata core(AssetType type, Class<? extends Asset> assetClass, boolean enabled)
    {
        return new ModuleMetadata(type, assetClass, enabled, false, Source.core);
    }

    /**
     * @return whether its assets can be processed: the module is enabled, and detected
     */
    public boolean active()
    {
        return this.enabled && !this.missing;
    }

    /**
     * @return whether assets of this type can be declared in an environment; simulated assets only exist within a
     *         simulation request, and a missing module takes no asset
     */
    @JsonProperty("creatable")
    public boolean creatable()
    {
        return !this.missing && !SimulatedAsset.class.isAssignableFrom(this.assetClass);
    }

    public enum Source
    {
        core,
        extension,
    }
}
