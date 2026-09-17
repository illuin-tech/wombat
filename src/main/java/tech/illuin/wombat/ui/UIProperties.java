package tech.illuin.wombat.ui;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import tech.illuin.wombat.commons.DurationConfig;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@ConfigMapping(prefix = "ui")
public interface UIProperties
{
    MaxDateRange maxDateRange();

    interface MaxDateRange
    {
        @WithDefault("31")
        long duration();

        @WithDefault("DAYS")
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
