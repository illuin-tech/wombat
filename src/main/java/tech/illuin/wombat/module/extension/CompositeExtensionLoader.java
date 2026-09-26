package tech.illuin.wombat.module.extension;

import tech.illuin.wombat.core.module.WombatModule;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class CompositeExtensionLoader implements ExtensionLoader
{
    private final List<ExtensionLoader> loaders;

    public CompositeExtensionLoader(List<ExtensionLoader> loaders)
    {
        this.loaders = loaders;
    }

    public CompositeExtensionLoader(ExtensionLoader... loaders)
    {
        this(Arrays.asList(loaders));
    }

    @Override
    public List<WombatModule> load()
    {
        return this.loaders.stream()
            .flatMap(loader -> loader.load().stream())
            .toList();
    }

    @Override
    public void close() throws IOException
    {
        for (ExtensionLoader loader : this.loaders)
            loader.close();
    }
}
