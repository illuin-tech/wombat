package tech.illuin.wombat.feature;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.stream.Collectors;

@ApplicationScoped
public class FeatureFlags
{
    private static final Logger logger = LoggerFactory.getLogger(FeatureFlags.class);

    private final Flags flags;

    public FeatureFlags(Flags flags)
    {
        this.flags = flags;
    }

    public Uni<Boolean> isEnabled(Feature feature)
    {
        return this.flags.find(feature.flag()).chain(flag -> flag
            .map(Flag::compute)
            .map(value -> value.map(Flag.Value::asBoolean))
            .orElseGet(() -> Uni.createFrom().item(false))
        );
    }

    void logFeatures(@Observes StartupEvent event)
    {
        String features = Arrays.stream(Feature.values())
            .map(feature -> feature.flag() + "=" + this.isEnabled(feature).await().indefinitely())
            .collect(Collectors.joining(", "));
        logger.info("Features: {}", features);
    }
}
