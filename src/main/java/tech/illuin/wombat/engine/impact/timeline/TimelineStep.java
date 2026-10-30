package tech.illuin.wombat.engine.impact.timeline;

import tech.illuin.wombat.core.activity.commons.TimeRange;

import java.time.Duration;

import static tech.illuin.wombat.core.activity.commons.TimeRange.toEpochMs;

public enum TimelineStep
{
    HOUR(Duration.ofHours(1).toMillis(), "hourly", "hours"),
    DAY(Duration.ofDays(1).toMillis(), "daily", "days");

    private final long millis;
    private final String label;
    private final String plural;

    TimelineStep(long millis, String label, String plural)
    {
        this.millis = millis;
        this.label = label;
        this.plural = plural;
    }

    public static TimelineStep of(TimeRange range)
    {
        long start = toEpochMs(range.start());
        long end = toEpochMs(range.end());
        if (start == Long.MIN_VALUE || end == Long.MAX_VALUE || end <= start)
            return DAY;
        return end - start <= TimeRange.HOURLY_MAX_SPAN.toMillis() ? HOUR : DAY;
    }

    public long millis()
    {
        return this.millis;
    }

    public String label()
    {
        return this.label;
    }

    public String plural()
    {
        return this.plural;
    }
}
