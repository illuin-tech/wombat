package tech.illuin.wombat.persistence.backend.api.store;

import tech.illuin.wombat.core.source.data.MetricData;

import java.util.Collection;

public interface MetricStore<T extends MetricData>
{
    void save(Collection<MetricSample<T>> samples);
}
