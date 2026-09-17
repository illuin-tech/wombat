package tech.illuin.wombat.environment.model;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusProfile;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIServerProfile;
import tech.illuin.wombat.module.llm_static.LLMStaticProfile;
import tech.illuin.wombat.core.asset.profile.ServerProvider;
import tech.illuin.wombat.context.model.AssetInfo;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.context.persistence.AssetEntity;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIAsset;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AssetInfoTest
{

    @Test
    void exposesKubernetesConnectionProfileAndTimestamps()
    {
        KubernetesAPIServerProfile profile = new KubernetesAPIServerProfile(ServerProvider.aws, "c5.large", "FRA", 43800);
        AssetEntity entity = stamp(AssetEntity.from("env", new KubernetesAPIAsset(
            "k", "env", "K", "/kube/config", "ns", Optional.empty(), Optional.empty(), 0, profile)));

        AssetInfo info = AssetInfo.from(entity);

        assertEquals("k", info.id());
        assertEquals("ns", info.namespace());
        assertEquals(profile, info.profile());
        assertEquals(Instant.ofEpochMilli(1), info.createdAt());
        assertEquals(Instant.ofEpochMilli(2), info.updatedAt());
        assertNull(info.deletedAt());
    }

    @Test
    void exposesPrometheusUrlAndUsernameButNeverACredential()
    {
        LLMPrometheusProfile profile = new LLMPrometheusProfile(LLMProvider.mistralai, "m", "FRA",
            new LLMPrometheusProfile.DynamicProfile("q"));
        AssetEntity entity = stamp(AssetEntity.from("env", new LLMPrometheusAsset(
            "p", "env", "P", "http://prometheus", null, "user", "PROM_PASSWORD", 5, profile)));

        AssetInfo info = AssetInfo.from(entity);

        assertEquals("http://prometheus", info.prometheusUrl());
        assertEquals("user", info.username());
        assertEquals(profile, info.profile());
        // The asset itself only carries `PROM_PASSWORD`, the variable name — the password lives in the environment
        // and is read at scrape time, so there is nothing for the API to leak.
    }

    @Test
    void exposesStaticProfileWithoutConnectionFields()
    {
        LLMStaticProfile profile = new LLMStaticProfile(LLMProvider.mistralai, "m", "FRA",
            new LLMStaticProfile.RequestProfile(500, 1000));
        AssetEntity entity = stamp(AssetEntity.from("env", new LLMStaticAsset("s", "env", "S", profile)));

        AssetInfo info = AssetInfo.from(entity);

        assertEquals(profile, info.profile());
        assertNull(info.prometheusUrl());
        assertNull(info.namespace());
    }

    private static AssetEntity stamp(AssetEntity entity)
    {
        entity.uuid = "uuid";
        entity.createdAt = Instant.ofEpochMilli(1);
        entity.updatedAt = Instant.ofEpochMilli(2);
        return entity;
    }
}
