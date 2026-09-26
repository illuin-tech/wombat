package tech.illuin.wombat.persistence.micrometer.data;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MetricGroup
{
    private final TagGroup tags;
    private final Map<String, Double> values;

    public MetricGroup(TagGroup tags)
    {
        this.tags = tags;
        this.values = new HashMap<>();
    }

    public void recordValues(Meter meter, Map<String, Aggregation> keys)
    {
        Aggregation aggregation = keys.get(meter.getId().getName());
        if (aggregation == null)
            return;
        if (!(meter instanceof DistributionSummary summary))
            return;

        long count = summary.count();
        if (count == 0L)
            return;

        this.values.put(meter.getId().getName(), aggregation.of(summary.totalAmount(), count));
    }

    public Optional<Double> value(String key)
    {
        return Optional.ofNullable(this.values.get(key));
    }

    public Optional<String> tag(String key)
    {
        return Optional.ofNullable(this.tags.get(key));
    }
}
