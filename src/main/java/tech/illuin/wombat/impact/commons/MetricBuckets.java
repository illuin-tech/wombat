package tech.illuin.wombat.impact.commons;


public final class MetricBuckets
{
    public static String expression(long stepMs)
    {
        if (stepMs <= 0)
            throw new IllegalArgumentException("A bucket step must be strictly positive, got " + stepMs);
        return "instant_ms - (instant_ms % " + stepMs + ")";
    }

    private MetricBuckets() {}
}
