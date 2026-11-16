package tech.illuin.wombat.feature;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.commons.NodeMode;
import tech.illuin.wombat.commons.WombatProperties;

import java.util.Arrays;
import java.util.stream.Collectors;

/** The features served by this instance, set by its mode. REST endpoints are gated on {@link Feature.Keys} instead. */
@ApplicationScoped
public class FeatureFlags
{
    private static final Logger logger = LoggerFactory.getLogger(FeatureFlags.class);

    private final NodeMode mode;

    public FeatureFlags(WombatProperties wombat)
    {
        this.mode = wombat.mode();
    }

    public boolean isEnabled(Feature feature)
    {
        return feature.enabledIn(this.mode);
    }

    void logFeatures(@Observes StartupEvent event)
    {
        String features = Arrays.stream(Feature.values())
            .map(feature -> feature.flag() + "=" + this.isEnabled(feature))
            .collect(Collectors.joining(", "));
        logger.info("Features: {}", features);
    }
}
