package tech.illuin.wombat.impact.timeline;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.activity.commons.TimeRange;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where the resolution flips, and that it flips on the span alone — the step is read off the range
 * the reader picked, never off what happens to be stored in it.
 */
class TimelineStepTest
{
    @Test
    void of_underAWeek_chartsByTheHour()
    {
        assertEquals(TimelineStep.HOUR, TimelineStep.of(spanning(Duration.ofDays(1))));
        assertEquals(TimelineStep.HOUR, TimelineStep.of(spanning(Duration.ofDays(6))));
    }

    /** The boundary is inclusive: a week is still worth 168 columns, where it would be worth 7 days. */
    @Test
    void of_atExactlyAWeek_chartsByTheHour()
    {
        assertEquals(TimelineStep.HOUR, TimelineStep.of(spanning(Duration.ofDays(7))));
    }

    @Test
    void of_pastAWeek_chartsByTheDay()
    {
        assertEquals(TimelineStep.DAY, TimelineStep.of(spanning(Duration.ofDays(7).plusMillis(1))));
        assertEquals(TimelineStep.DAY, TimelineStep.of(spanning(Duration.ofDays(90))));
        assertEquals(TimelineStep.DAY, TimelineStep.of(spanning(Duration.ofDays(366))));
    }

    /** No countable span, so no buckets downstream either — the step just has to be an answer. */
    @Test
    void of_withAnOpenEndedRange_chartsByTheDay()
    {
        assertEquals(TimelineStep.DAY, TimelineStep.of(new TimeRange(Instant.MIN, Instant.MAX)));
    }

    @Test
    void millis_matchTheStepsTheyName()
    {
        assertEquals(Duration.ofHours(1).toMillis(), TimelineStep.HOUR.millis());
        assertEquals(Duration.ofDays(1).toMillis(), TimelineStep.DAY.millis());
    }

    private static TimeRange spanning(Duration span)
    {
        Instant start = Instant.parse("2026-03-01T00:00:00Z");
        return new TimeRange(start, start.plus(span));
    }
}
