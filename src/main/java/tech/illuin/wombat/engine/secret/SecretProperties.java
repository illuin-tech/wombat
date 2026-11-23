package tech.illuin.wombat.engine.secret;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "wombat.secret")
public interface SecretProperties
{
    @WithDefault("disabled")
    WriteMode writeMode();

    Directory directory();

    Keystore keystore();

    Database database();

    Vault vault();

    interface Directory
    {
        Optional<String> path();
    }

    interface Keystore
    {
        Optional<String> path();

        Optional<String> password();

        @WithDefault("PKCS12")
        String type();
    }

    interface Database
    {
        @WithName("active-kek-id")
        Optional<String> activeKekId();

        Map<String, String> kek();
    }

    interface Vault
    {
        /** Adds Vault as a read-only resolver, or a read-write one with {@link WriteMode#vault}. */
        @WithDefault("false")
        boolean enabled();

        @WithName("path-prefix")
        @WithDefault("wombat")
        String pathPrefix();
    }

    enum WriteMode
    {
        disabled,
        database,
        vault
    }
}
