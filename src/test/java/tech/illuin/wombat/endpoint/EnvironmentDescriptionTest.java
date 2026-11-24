package tech.illuin.wombat.endpoint;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.profile.Lifetime;
import tech.illuin.wombat.core.secret.SecretKey;
import tech.illuin.wombat.core.source.Period;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPICloudProfile;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;
import tech.illuin.wombat.module.llm_static.LLMStaticProfile;
import tech.illuin.wombat.core.asset.profile.ServerProvider;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.module.llm_static.scenario.ConstantScenario;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static tech.illuin.wombat.core.activity.commons.Country.FRANCE;

class EnvironmentDescriptionTest
{
    @Test
    void fromMapsEnvironmentAndAssetSummaries()
    {
        Environment environment = new Environment("Production", List.of(
            new KubernetesAPIAsset(
                AssetIdentity.of("cluster-1", "Production", KubernetesAPIModule.TYPE),
                new SecretKey("kubeconfig"), Set.of("ns"), Optional.empty(), Optional.empty(), Period.ZERO,
                new KubernetesAPICloudProfile(ServerProvider.aws, "c5.large", FRANCE, Lifetime.ofYears(5))
            ),
            new LLMStaticAsset(
                AssetIdentity.of("llm-1", "Production", LLMStaticModule.TYPE),
                new LLMStaticProfile(LLMProvider.mistralai, "mistral-large-latest", FRANCE, 1000, 500, List.of(
                    new ConstantScenario()
                ))
            )
        ));
        TimeRange timeRange = new TimeRange(Instant.EPOCH, Instant.EPOCH.plusSeconds(60));

        EnvironmentDescription config = EnvironmentDescription.from("prod", environment, timeRange);

        assertEquals("prod", config.id());
        assertEquals("Production", config.name());
        assertEquals(timeRange, config.timeRange());
        assertEquals(2, config.assets().size());
        assertEquals(new EnvironmentDescription.AssetSummary("cluster-1", "cluster-1", KubernetesAPIModule.TYPE), config.assets().getFirst());
        assertEquals(new EnvironmentDescription.AssetSummary("llm-1", "llm-1", LLMStaticModule.TYPE), config.assets().getLast());
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
