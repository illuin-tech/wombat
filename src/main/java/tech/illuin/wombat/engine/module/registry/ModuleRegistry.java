package tech.illuin.wombat.engine.module.registry;

import tech.illuin.wombat.core.asset.type.AssetType;

import java.util.List;
import java.util.Optional;

public interface ModuleRegistry
{
    /**
     * @return every registered module, missing ones included
     */
    List<ModuleMetadata> listModules();

    /**
     * @return the registered module, which may be missing: see {@link ModuleMetadata#active()}
     */
    Optional<ModuleMetadata> getModule(String module);

    default Optional<ModuleMetadata> getModule(AssetType type)
    {
        return this.getModule(type.name());
    }

    /**
     * @param user who toggles the module, null when authentication is disabled
     * @throws UnknownModuleException if the module is not registered
     * @throws ModuleConflictException if the module is missing
     */
    ModuleMetadata setEnabled(String module, boolean enabled, String user);

    /**
     * Forgets a missing module. Should it be detected again, it is registered afresh, enabled.
     *
     * @throws UnknownModuleException if the module is not registered
     * @throws ModuleConflictException if the module is detected
     */
    void remove(String module);
}
