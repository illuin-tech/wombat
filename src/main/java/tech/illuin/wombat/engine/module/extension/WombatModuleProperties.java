package tech.illuin.wombat.engine.module.extension;

import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.nio.file.Path;

/**
 * Whether a module is enabled is no longer configured here: it is the registry's state, kept in the database.
 */
public interface WombatModuleProperties
{
    @WithName("extension")
    ExtensionProperties extension();

    interface ExtensionProperties
    {
        @WithName("path")
        @WithDefault("extensions")
        Path path();
    }
}
