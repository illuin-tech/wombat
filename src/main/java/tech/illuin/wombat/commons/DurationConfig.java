package tech.illuin.wombat.commons;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.DefaultValue;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public record DurationConfig(long duration, ChronoUnit unit) {
    public DurationConfig(@JsonProperty("duration") @DefaultValue("5") long duration, @JsonProperty("unit") @DefaultValue("SECONDS") ChronoUnit unit) {
        this.duration = duration;
        this.unit = unit;
    }

    public static DurationConfig defaultDuration() {
        return new DurationConfig(5L, ChronoUnit.SECONDS);
    }

    public static DurationConfig of(Duration duration) {
        return new DurationConfig(duration.toMillis(), ChronoUnit.MILLIS);
    }

    public Duration asDuration() {
        return this.unit().getDuration().multipliedBy(this.duration());
    }

    @JsonProperty("duration")
    public long duration() {
        return this.duration;
    }

    @JsonProperty("unit")
    public ChronoUnit unit() {
        return this.unit;
    }
}
