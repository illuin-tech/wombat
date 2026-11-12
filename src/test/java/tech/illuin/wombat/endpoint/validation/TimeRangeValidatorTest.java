package tech.illuin.wombat.endpoint.validation;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.activity.commons.TimeRange;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TimeRangeValidatorTest
{
    private final TimeRangeValidator validator = new TimeRangeValidator();

    @Test
    void nullRangeIsValid()
    {
        assertTrue(this.validator.isValid(null, null));
    }

    @Test
    void missingEndpointsAreInvalid()
    {
        Instant instant = Instant.parse("2026-01-01T00:00:00Z");
        TimeRange missingStart = mock(TimeRange.class);
        when(missingStart.end()).thenReturn(instant);
        TimeRange missingEnd = mock(TimeRange.class);
        when(missingEnd.start()).thenReturn(instant);

        assertFalse(this.validator.isValid(missingStart, null));
        assertFalse(this.validator.isValid(missingEnd, null));
    }

    @Test
    void equalOrReversedEndpointsAreInvalid()
    {
        Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        assertFalse(this.validator.isValid(new TimeRange(instant, instant), null));
        assertFalse(this.validator.isValid(new TimeRange(instant.plusSeconds(1), instant), null));
    }

    @Test
    void increasingEndpointsAreValid()
    {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");

        assertTrue(this.validator.isValid(new TimeRange(start, start.plusSeconds(1)), null));
    }
}