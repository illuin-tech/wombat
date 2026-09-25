package tech.illuin.wombat.impact.timeline;

import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.persistence.micrometer.MetricsConfig;

import java.time.Duration;

import static tech.illuin.wombat.core.activity.commons.TimeRange.toEpochMs;

public enum TimelineStep
{
    HOUR(MetricsConfig.COMPACTION_MILLIS, "hourly", "hours"),
    DAY(Duration.ofDays(1).toMillis(), "daily", "days");

    private static final long INTRADAY_MAX_SPAN_MS = Duration.ofDays(7).toMillis();

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
        return end - start <= INTRADAY_MAX_SPAN_MS ? HOUR : DAY;
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
