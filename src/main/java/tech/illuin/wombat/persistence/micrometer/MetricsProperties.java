package tech.illuin.wombat.persistence.micrometer;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import tech.illuin.wombat.commons.DurationConfig;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@ConfigMapping(prefix = "metrics")
public interface MetricsProperties
{
    AggregationWindow aggregationWindow();

    interface AggregationWindow
    {
        @WithDefault("5")
        long duration();

        @WithDefault("MINUTES")
        ChronoUnit unit();

        default Duration asDuration()
        {
            return this.unit().getDuration().multipliedBy(this.duration());
        }

        default DurationConfig toDurationConfig()
        {
            return new DurationConfig(this.duration(), this.unit());
        }
    }
}
