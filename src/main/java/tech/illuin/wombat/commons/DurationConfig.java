package tech.illuin.wombat.commons;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public record DurationConfig(
    @JsonProperty("duration") long duration,
    @JsonProperty("unit") ChronoUnit unit
) {
    public static DurationConfig defaultDuration() {
        return new DurationConfig(5L, ChronoUnit.SECONDS);
    }

    public static DurationConfig of(Duration duration) {
        return new DurationConfig(duration.toMillis(), ChronoUnit.MILLIS);
    }

    public Duration asDuration() {
        return this.unit().getDuration().multipliedBy(this.duration());
    }
}
