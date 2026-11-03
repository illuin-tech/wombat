package tech.illuin.wombat.engine.impact.commons;


public final class MetricBuckets
{
    public static String expression(long stepMs)
    {
        if (stepMs <= 0)
            throw new IllegalArgumentException("A bucket step must be strictly positive, got " + stepMs);
        return "instant_ms - (instant_ms % " + stepMs + ")";
    }

    public static String expression(long startMs, long stepMs)
    {
        if (stepMs <= 0)
            throw new IllegalArgumentException("A bucket step must be strictly positive, got " + stepMs);
        return "(" + startMs + " + ((instant_ms - " + startMs + ") / " + stepMs + ") * " + stepMs + ")";
    }

    private MetricBuckets() {}
}
