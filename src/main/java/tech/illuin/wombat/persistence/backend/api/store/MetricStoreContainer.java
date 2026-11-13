package tech.illuin.wombat.persistence.backend.api.store;

import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.source.data.MetricData;

import java.util.*;
import java.util.stream.Collectors;

public class MetricStoreContainer
{
    private final Map<ServiceFamily, MetricStore<?>> stores;

    public MetricStoreContainer(Map<ServiceFamily, MetricStore<?>> stores)
    {
        this.stores = Map.copyOf(stores);
    }

    public boolean supports(ServiceFamily family)
    {
        return this.stores.containsKey(family);
    }

    @SuppressWarnings("unchecked")
    public <T extends MetricData> Optional<MetricStore<T>> get(ServiceFamily family)
    {
        return Optional.ofNullable((MetricStore<T>) this.stores.get(family));
    }

    /**
     * @throws IllegalStateException if a sample is of a service family without a store, in which case none is saved
     */
    public void save(Collection<? extends MetricSample<? extends MetricData>> samples)
    {
        Map<ServiceFamily, List<MetricSample<MetricData>>> byFamily = samples.stream()
            .map(MetricStoreContainer::widen)
            .collect(Collectors.groupingBy(sample -> sample.data().serviceFamily(), LinkedHashMap::new, Collectors.toList()));

        for (ServiceFamily family : byFamily.keySet())
        {
            if (!this.supports(family))
                throw new IllegalStateException("No store for service family " + family + ", stores are " + this.stores.keySet());
        }

        byFamily.forEach((family, familySamples) -> this.<MetricData>get(family).orElseThrow().save(familySamples));
    }

    @SuppressWarnings("unchecked")
    private static MetricSample<MetricData> widen(MetricSample<? extends MetricData> sample)
    {
        return (MetricSample<MetricData>) sample;
    }
}
