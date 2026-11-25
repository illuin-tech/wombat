package tech.illuin.wombat.engine.module.settings;

import java.util.List;

/**
 * Settings given to the modules which they cannot use: each problem names its key, so that one restart fixes them all.
 */
public class ModuleSettingsException extends RuntimeException
{
    private final List<String> problems;

    public ModuleSettingsException(List<String> problems)
    {
        super("Invalid module settings under " + ModuleSettingsReader.PREFIX + ":\n - " + String.join("\n - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems()
    {
        return this.problems;
    }
}
