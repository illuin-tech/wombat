package tech.illuin.wombat.persistence.model;

import jakarta.persistence.Converter;
import tech.illuin.wombat.core.source.data.KubernetesData;

@Converter
public class KubernetesDataConverter extends MetricDataConverter<KubernetesData>
{
    public KubernetesDataConverter()
    {
        super(KubernetesData.class);
    }
}
