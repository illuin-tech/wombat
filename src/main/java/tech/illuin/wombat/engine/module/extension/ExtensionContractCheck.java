package tech.illuin.wombat.engine.module.extension;

import tech.illuin.wombat.core.module.WombatModule;

import java.util.Optional;

final class ExtensionContractCheck
{
    private ExtensionContractCheck() {}

    /**
     * @return performs cursory validity checks and eventually returns a description of non-critical module violations
     */
    static Optional<String> check(WombatModule module)
    {
        if (module.type() == null || module.type().name().isBlank())
            throw new IllegalArgumentException("Extension module " + module.getClass().getName() + " has invalid type");
        if (module.assetClass() == null || module.settingsClass() == null)
            throw new IllegalArgumentException("Extension module " + module.getClass().getName() + " has null assetClass or settingsClass, expected an Optional");
        /* Whether the asset class matches the type is checked by the core, which registers the module */

        return Optional.empty();
    }
}
