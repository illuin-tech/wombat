package tech.illuin.wombat.engine.module.registry;

import tech.illuin.wombat.core.asset.type.ModuleType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    default Optional<ModuleMetadata> getModule(ModuleType type)
    {
        return this.getModule(type.name());
    }

    /**
     * @return the modules registered for the first time when this instance started, enabled as every new module is;
     *         empty for a registry with no startup registration
     */
    default Set<String> newlyRegistered()
    {
        return Set.of();
    }

    /**
     * @param user who toggles the module, null when authentication is disabled
     * @throws UnknownModuleException if the module is not registered
     * @throws ModuleConflictException if the module is missing
     */
    ModuleMetadata setEnabled(String module, boolean enabled, String user);

    /**
     * Sets the order the family modules of {@code family} are tried in; the core follows it once redeployed.
     *
     * @param modules every family module registered for {@code family}, disabled and missing ones included, the first
     *                one tried first
     * @param user    who reorders them, null when authentication is disabled
     * @throws ModuleOrderException if {@code modules} does not list each of them exactly once
     */
    void setFamilyOrder(ServiceFamily family, List<String> modules, String user);

    /**
     * Forgets a missing module. Should it be detected again, it is registered afresh, enabled.
     *
     * @throws UnknownModuleException if the module is not registered
     * @throws ModuleConflictException if the module is detected
     */
    void remove(String module);
}
