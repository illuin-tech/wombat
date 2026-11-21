package tech.illuin.wombat.engine.module.registry;

import com.fasterxml.jackson.annotation.JsonProperty;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.SimulatedAsset;
import tech.illuin.wombat.core.asset.type.AssetType;

/**
 * A registered module, as the registry knows it.
 *
 * @param type       for a module not detected here, as it advertised it when last detected
 * @param assetClass null when this instance does not detect the module
 * @param enabled    the registry state, shared by every instance
 * @param missing    whether no live instance detects the module any more, e.g. its extension jar was removed
 * @param source     whether it comes from the extension folder; null when this instance does not detect the module
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
     * @return whether this instance detects the module, and so holds its classes
     */
    public boolean detectedHere()
    {
        return this.assetClass != null;
    }

    /**
     * @return whether its assets can be processed here: the module is enabled, and detected here
     */
    public boolean active()
    {
        return this.enabled && this.detectedHere();
    }

    /**
     * @return whether assets of this type can be declared in an environment; simulated assets only exist within a
     *         simulation request, and a module not detected here takes no asset. The simulation sandbox holds the same
     *         types, so a new reason here also keeps a type out of it.
     */
    @JsonProperty("creatable")
    public boolean creatable()
    {
        return this.detectedHere() && !SimulatedAsset.class.isAssignableFrom(this.assetClass);
    }

    public enum Source
    {
        core,
        extension,
    }
}
