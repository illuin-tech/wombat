package tech.illuin.wombat.engine.module.registry;

import tech.illuin.wombat.core.asset.type.AssetType;

import java.util.List;
import java.util.Optional;

public interface ModuleRegistry
{
    List<ModuleMetadata> listModules();

    Optional<ModuleMetadata> getModule(String module);

    default Optional<ModuleMetadata> getModule(AssetType type)
    {
        return this.getModule(type.name());
    }
}
