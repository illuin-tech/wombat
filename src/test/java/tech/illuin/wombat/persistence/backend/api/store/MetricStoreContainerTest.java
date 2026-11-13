package tech.illuin.wombat.persistence.backend.api.store;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.activity.commons.Country;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.core.source.data.LLMData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetricStoreContainerTest
{
    private static final AssetIdentity ASSET = AssetIdentity.of("asset", "env", "Asset", "tech.illuin.wombat-module.test");

    private final List<MetricSample<KubernetesData>> kubernetes = new ArrayList<>();
    private final List<MetricSample<LLMData>> llm = new ArrayList<>();

    @Test
    void save_sendsEachSampleToTheStoreOfItsServiceFamily()
    {
        MetricStoreContainer stores = new MetricStoreContainer(Map.of(
            ServiceFamily.KUBERNETES_CONTAINER, (MetricStore<KubernetesData>) this.kubernetes::addAll,
            ServiceFamily.LLM, (MetricStore<LLMData>) this.llm::addAll
        ));
        MetricSample<KubernetesData> container = MetricSample.of(ASSET, 0, 1000, new KubernetesData("svc", "cluster", "ns", "pod", 1.0, 2.0));
        MetricSample<LLMData> model = MetricSample.of(ASSET, 0, 1000, new LLMData("svc", LLMProvider.mistralai, "mistral-large-latest", Country.FRANCE, 10L, 1L));

        stores.save(List.of(container, model));

        assertEquals(List.of(container), this.kubernetes);
        assertEquals(List.of(model), this.llm);
    }

    @Test
    void save_savesNothingWhenASampleHasNoStore()
    {
        MetricStoreContainer stores = new MetricStoreContainer(Map.of(
            ServiceFamily.KUBERNETES_CONTAINER, (MetricStore<KubernetesData>) this.kubernetes::addAll
        ));
        MetricSample<KubernetesData> container = MetricSample.of(ASSET, 0, 1000, new KubernetesData("svc", "cluster", "ns", "pod", 1.0, 2.0));
        MetricSample<LLMData> model = MetricSample.of(ASSET, 0, 1000, new LLMData("svc", LLMProvider.mistralai, "mistral-large-latest", Country.FRANCE, 10L, 1L));

        assertThrows(IllegalStateException.class, () -> stores.save(List.of(container, model)));
        assertTrue(this.kubernetes.isEmpty());
        assertFalse(stores.supports(ServiceFamily.LLM));
    }
}
