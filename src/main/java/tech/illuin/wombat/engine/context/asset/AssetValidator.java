package tech.illuin.wombat.engine.context.asset;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.engine.context.asset.model.UnrecognizedAsset;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;

/**
 * Checks an asset received through the API (pushed by a worker or created by a user) before it is persisted: its type
 * must be known, and have a module on this instance. A module disabled here does not reject the asset, its metrics are
 * skipped instead.
 */
@ApplicationScoped
public class AssetValidator
{
    private static final Logger logger = LoggerFactory.getLogger(AssetValidator.class);

    private final ModuleRegistry modules;

    public AssetValidator(ModuleRegistry modules)
    {
        this.modules = modules;
    }

    public void validate(Asset asset) throws InvalidAssetException
    {
        if (asset instanceof UnrecognizedAsset unrecognized)
            throw new InvalidAssetException("Unknown asset type " + unrecognized.rawType() + " for asset " + asset.identity().id());

        ModuleMetadata module = this.modules.getModule(asset.type())
            .filter(candidate -> !candidate.missing())
            .orElseThrow(() -> new InvalidAssetException("No module for asset type " + asset.type().name() + " of asset " + asset.identity().id()));
        if (!module.enabled())
            logger.debug("Asset {} is declared with asset type {} whose module is disabled, its metrics will be skipped", asset.identity().id(), asset.type().name());
    }
}
