package tech.illuin.wombat.commons;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import tech.illuin.wombat.persistence.backend.push.WorkerProperties;

import java.util.Optional;

@ConfigMapping(prefix = "wombat")
public interface WombatProperties
{
    /**
     * Role of this instance, see {@link NodeMode}.
     */
    @WithDefault("standalone")
    NodeMode mode();

    SecretProperties secret();

    WorkerProperties worker();

    /**
     * Where the credentials referenced by assets are resolved from, on top of the environment variables.
     */
    interface SecretProperties
    {
        DirectoryProperties directory();

        KeyStoreProperties keystore();
    }

    interface DirectoryProperties
    {
        /** A directory holding one file per secret, named after its key. */
        Optional<String> path();
    }

    interface KeyStoreProperties
    {
        Optional<String> path();

        Optional<String> password();

        @WithDefault("PKCS12")
        String type();
    }
}
