package tech.illuin.wombat.persistence.store;

import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.core.source.data.LLMData;

public record MetricStores(
    MetricStore<KubernetesData> kubernetes,
    MetricStore<LLMData> llm
) {}
