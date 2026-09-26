package tech.illuin.wombat.module.extension;

import tech.illuin.wombat.core.module.WombatModule;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

@FunctionalInterface
public interface ExtensionLoader extends Closeable, AutoCloseable
{
    List<WombatModule> load();

    @Override
    default void close() throws IOException {}
}
