package tech.illuin.wombat.persistence.store;

import tech.illuin.wombat.core.source.data.MetricData;

import java.util.Collection;

/**
 * Where aggregated samples of a given {@link MetricData} variant end up, be it a local database or a remote Wombat.
 */
public interface MetricStore<T extends MetricData>
{
    void save(Collection<MetricSample<T>> samples);
}
