package tech.illuin.wombat.persistence.micrometer.data;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class MetricGroup
{
    private final TagGroup tags;
    private final Map<String, Double> values;

    public MetricGroup(TagGroup tags)
    {
        this.tags = tags;
        this.values = new HashMap<>();
    }

    public void recordValues(Meter meter, Set<String> keys)
    {
        for (String key : keys)
        {
            if (!meter.getId().getName().equals(key))
                continue;
            if (!(meter instanceof DistributionSummary summary))
                continue;

            long count = summary.count();
            if (count == 0L)
                continue;

            double mean = summary.totalAmount() / count;

            this.values.put(key, mean);
            return;
        }
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
