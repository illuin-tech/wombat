package tech.illuin.wombat.environment.persistence;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusProfile;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIServerProfile;
import tech.illuin.wombat.module.llm_static.LLMStaticProfile;
import tech.illuin.wombat.core.asset.profile.ServerProvider;
import tech.illuin.wombat.context.persistence.AssetEntity;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIAsset;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;
import tech.illuin.wombat.core.asset.AssetType;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetEntityTest
{
    @Test
    void kubernetesPropertiesRoundTripThroughTheEntity()
    {
        KubernetesAPIAsset props = new KubernetesAPIAsset(
            "k", "env", "K", "/kube/config", "ns", Optional.of("ctx"), Optional.of(Duration.ofSeconds(5)), 3,
            new KubernetesAPIServerProfile(ServerProvider.aws, "c5.large", "FRA", 43800));

        AssetEntity entity = AssetEntity.from("env", props);

        assertEquals("env", entity.environmentId);
        assertEquals(AssetType.KUBERNETES_API, entity.type);
        assertEquals(props, entity.toProperties());
    }

    @Test
    void llmStaticPropertiesRoundTripThroughTheEntity()
    {
        LLMStaticAsset props = new LLMStaticAsset("s", "env", "S",
            new LLMStaticProfile(LLMProvider.mistralai, "m", "FRA",
                new LLMStaticProfile.RequestProfile(500, 1000)));

        AssetEntity entity = AssetEntity.from("env", props);

        assertEquals(AssetType.LLM_STATIC, entity.type);
        assertEquals(props, entity.toProperties());
    }

    @Test
    void llmPrometheusPropertiesRoundTripThroughTheEntity()
    {
        LLMPrometheusAsset props = new LLMPrometheusAsset("p", "env", "P",
            "http://prometheus", "http://proxy:8080", "user", "PROM_PASSWORD", 7,
            new LLMPrometheusProfile(LLMProvider.mistralai, "m", "FRA",
                new LLMPrometheusProfile.DynamicProfile("q")));

        AssetEntity entity = AssetEntity.from("env", props);

        assertEquals(AssetType.LLM_PROMETHEUS, entity.type);
        assertEquals(props, entity.toProperties());
    }
}
