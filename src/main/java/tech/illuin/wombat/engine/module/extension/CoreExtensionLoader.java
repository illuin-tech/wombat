package tech.illuin.wombat.engine.module.extension;

import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.module.CoreModules;

import java.util.List;

/**
 * The modules of {@code wombat-core-module}, its family modules last in the order they are tried in among those of a
 * family.
 */
public class CoreExtensionLoader implements ExtensionLoader
{
    @Override
    public List<WombatModule> load()
    {
        return CoreModules.all();
    }
}
