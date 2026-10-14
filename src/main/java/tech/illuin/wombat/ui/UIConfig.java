package tech.illuin.wombat.ui;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import io.quarkus.runtime.LaunchMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@ApplicationScoped
public class UIConfig
{
    @Produces
    @Singleton
    public PebbleEngine templateEngine()
    {
        ClasspathLoader loader = new ClasspathLoader(UIConfig.class.getClassLoader());
        loader.setPrefix("templates");
        loader.setSuffix(".peb");
        return new PebbleEngine.Builder()
            .loader(loader)
            .strictVariables(true)
            .autoEscaping(true)
            .cacheActive(LaunchMode.current() != LaunchMode.DEVELOPMENT)
            .extension(new WombatPebbleExtension())
            .build();
    }
}
