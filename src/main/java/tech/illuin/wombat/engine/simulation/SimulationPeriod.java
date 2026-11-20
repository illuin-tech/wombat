package tech.illuin.wombat.engine.simulation;

import java.time.Duration;

/**
 * The durations a simulation is projected over, and the reference windows it is evaluated on. A month and a year are
 * fixed durations here, not calendar ones.
 */
public enum SimulationPeriod
{
    DAY(Duration.ofDays(1)),
    WEEK(Duration.ofDays(7)),
    MONTH(Duration.ofDays(30)),
    YEAR(Duration.ofDays(365)),
    ;

    private final Duration duration;

    SimulationPeriod(Duration duration)
    {
        this.duration = duration;
    }

    public Duration duration()
    {
        return this.duration;
    }
}
