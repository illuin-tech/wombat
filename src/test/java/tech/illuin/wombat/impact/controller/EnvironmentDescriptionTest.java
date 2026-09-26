package tech.illuin.wombat.impact.controller;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIServerProfile;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;
import tech.illuin.wombat.module.llm_static.LLMStaticProfile;
import tech.illuin.wombat.core.asset.profile.ServerProvider;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.activity.commons.TimeRange;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnvironmentDescriptionTest
{
    @Test
    void fromMapsEnvironmentAndAssetSummaries()
    {
        Environment environment = new Environment("Production", List.of(
            new KubernetesAPIAsset("cluster-1", "Production", "Cluster One", "/kube/config", "ns", Optional.empty(), Optional.empty(), 0,
                new KubernetesAPIServerProfile(ServerProvider.aws, "c5.large", "FRA", 43800)),
            new LLMStaticAsset("llm-1", "Production", "LLM One",
                new LLMStaticProfile(LLMProvider.mistralai, "mistral-large-latest", "FRA", new LLMStaticProfile.RequestProfile(500, 1000)))
        ));
        TimeRange timeRange = new TimeRange(Instant.EPOCH, Instant.EPOCH.plusSeconds(60));

        EnvironmentDescription config = EnvironmentDescription.from("prod", environment, timeRange);

        assertEquals("prod", config.id());
        assertEquals("Production", config.name());
        assertEquals(timeRange, config.timeRange());
        assertEquals(2, config.assets().size());
        assertEquals(new EnvironmentDescription.AssetSummary("cluster-1", "Cluster One", KubernetesAPIModule.TYPE), config.assets().getFirst());
        assertEquals(new EnvironmentDescription.AssetSummary("llm-1", "LLM One", LLMStaticModule.TYPE), config.assets().getLast());
    }

    @Test
    void fromWithoutTimeRangeLeavesItNull()
    {
        Environment environment = new Environment("Production", List.of());

        EnvironmentDescription config = EnvironmentDescription.from("prod", environment);

        assertNull(config.timeRange());
        assertEquals(0, config.assets().size());
    }
}
