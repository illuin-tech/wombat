package tech.illuin.wombat.engine.module.registry;

import com.fasterxml.jackson.annotation.JsonProperty;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.SimulatedAsset;
import tech.illuin.wombat.core.asset.type.AssetType;

public record ModuleMetadata(
    AssetType type,
    Class<? extends Asset> assetClass,
    boolean enabled
) {
    /**
     * @return whether assets of this type can be declared in an environment; simulated assets only exist within a
     *         simulation request
     */
    @JsonProperty("creatable")
    public boolean creatable()
    {
        return !SimulatedAsset.class.isAssignableFrom(this.assetClass);
    }
}
