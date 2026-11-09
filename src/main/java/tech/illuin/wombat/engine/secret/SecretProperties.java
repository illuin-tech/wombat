package tech.illuin.wombat.engine.secret;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithConverter;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import org.eclipse.microprofile.config.spi.Converter;

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

    enum WriteMode
    {
        disabled,
        database
    }

    class WriteModeConverter implements Converter<WriteMode>
    {
        @Override
        public WriteMode convert(String value)
        {
            return WriteMode.valueOf(value);
        }
    }
}
