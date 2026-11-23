package tech.illuin.wombat.feature;

import io.quarkus.runtime.annotations.StaticInitSafe;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.PropertiesConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSource;
import tech.illuin.wombat.commons.NodeMode;
import tech.illuin.wombat.engine.secret.SecretProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static tech.illuin.wombat.commons.ConfigSourceFactories.FEATURE_FLAG;

/** Sets the {@link Feature.Keys} from the node mode, so endpoints are gated before the application starts. */
@StaticInitSafe
public class FeatureConfigSourceFactory implements ConfigSourceFactory
{
    static final String SECRET_WRITE_MODE = "wombat.secret.write-mode";

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context)
    {
        String value = context.getValue("wombat.mode").getValue();
        NodeMode mode = value == null ? NodeMode.standalone : NodeMode.parse(value);

        Map<String, String> properties = new HashMap<>();
        for (Feature feature : Feature.values())
            properties.put(feature.configKey(), String.valueOf(feature.enabledIn(mode)));

        boolean secretsWritable = isSecretWritable(context.getValue(SECRET_WRITE_MODE).getValue());
        properties.put(Feature.Keys.ADMIN_SECRETS, String.valueOf(Feature.ADMIN.enabledIn(mode) && secretsWritable));

        return List.of(new PropertiesConfigSource(
            properties,
            FeatureConfigSourceFactory.class.getSimpleName(),
            FEATURE_FLAG.priority()
        ));
    }

    private static boolean isSecretWritable(String writeMode)
    {
        return writeMode != null
            && !writeMode.isBlank()
            && !writeMode.strip().equalsIgnoreCase(SecretProperties.WriteMode.disabled.name());
    }

    @Override
    public OptionalInt getPriority()
    {
        return OptionalInt.of(FEATURE_FLAG.priority());
    }
}
