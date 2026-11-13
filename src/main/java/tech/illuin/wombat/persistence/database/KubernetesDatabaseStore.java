package tech.illuin.wombat.persistence.database;

import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.engine.impact.kubernetes.KubernetesMetricRepository;
import tech.illuin.wombat.persistence.model.KubernetesMetricEntity;
import tech.illuin.wombat.persistence.backend.api.store.MetricSample;
import tech.illuin.wombat.persistence.backend.api.store.MetricStore;

import java.util.Collection;

public class KubernetesDatabaseStore implements MetricStore<KubernetesData>
{
    private final KubernetesMetricRepository repository;

    public KubernetesDatabaseStore(KubernetesMetricRepository repository)
    {
        this.repository = repository;
    }

    @Override
    public void save(Collection<MetricSample<KubernetesData>> samples)
    {
        this.repository.save(samples.stream().map(KubernetesDatabaseStore::toEntity).toList());
    }

    private static KubernetesMetricEntity toEntity(MetricSample<KubernetesData> sample)
    {
        KubernetesMetricEntity row = new KubernetesMetricEntity();
        row.instantMs = sample.instantMs();
        row.windowMs = sample.windowMs();
        row.assign(sample.asset());
        row.data = sample.data();
        row.cpuNanocores = sample.data().cpuNanocores();
        row.ramBytes = sample.data().ramBytes();
        return row;
    }
}
