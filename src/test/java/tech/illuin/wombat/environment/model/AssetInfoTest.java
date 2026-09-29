package tech.illuin.wombat.environment.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.context.model.AssetInfo;
import tech.illuin.wombat.context.persistence.AssetEntity;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.asset.profile.AssetProfile;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.asset.profile.ServerProvider;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIAsset;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIServerProfile;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusAsset;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusProfile;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;
import tech.illuin.wombat.module.llm_static.LLMStaticProfile;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetInfoTest
{
    private ObjectMapper mapper;

    @BeforeEach
    void setUp()
    {
        mapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS)
            .build();
        mapper.registerSubtypes(
            new NamedType(KubernetesAPIAsset.class, "KUBERNETES_API"),
            new NamedType(LLMPrometheusAsset.class, "LLM_PROMETHEUS"),
            new NamedType(LLMStaticAsset.class, "LLM_STATIC")
        );
    }

    @Test
    void exposesKubernetesConnectionProfileAndTimestamps() throws Exception
    {
        KubernetesAPIServerProfile profile = new KubernetesAPIServerProfile(ServerProvider.aws, "c5.large", "FRA", 43800);
        AssetEntity entity = stamp(AssetEntity.from("env", new KubernetesAPIAsset(
            AssetIdentity.of("k", "env", "K"), "/kube/config", "ns", Optional.empty(), Optional.empty(), 0, profile)));

        AssetInfo info = AssetInfo.from(entity);

        assertEquals(entity.properties, info.properties());
        assertInstanceOf(KubernetesAPIAsset.class, info.properties());
        KubernetesAPIAsset k8s = (KubernetesAPIAsset) info.properties();
        assertEquals("k", k8s.identity().id());
        assertEquals("ns", k8s.namespace());
        assertEquals(profile, k8s.profile());
        assertEquals("uuid", info.uuid());
        assertEquals(Instant.ofEpochMilli(1), info.createdAt());
        assertEquals(Instant.ofEpochMilli(2), info.updatedAt());
        assertNull(info.deletedAt());

        JsonNode json = mapper.readTree(mapper.writeValueAsString(info));
        assertEquals("k", json.get("id").asText());
        assertEquals("K", json.get("name").asText());
        assertEquals(KubernetesAPIModule.TYPE.name(), json.get("type").asText());
        assertEquals("/kube/config", json.get("config-path").asText());
        assertEquals("ns", json.get("namespace").asText());
        assertEquals("c5.large", json.get("profile").get("instance-type").asText());
        assertEquals("uuid", json.get("uuid").asText());
        assertTrue(json.has("created-at"));
        assertTrue(json.has("updated-at"));
        assertFalse(json.has("deleted-at"));
    }

    @Test
    void exposesPrometheusUrlAndUsernameButNeverACredential() throws Exception
    {
        LLMPrometheusProfile profile = new LLMPrometheusProfile(LLMProvider.mistralai, "m", "FRA",
            new LLMPrometheusProfile.DynamicProfile("q"));
        AssetEntity entity = stamp(AssetEntity.from("env", new LLMPrometheusAsset(
            AssetIdentity.of("p", "env", "P"), "http://prometheus", null, "user", "PROM_PASSWORD", 5, profile)));

        AssetInfo info = AssetInfo.from(entity);

        assertInstanceOf(LLMPrometheusAsset.class, info.properties());
        LLMPrometheusAsset prom = (LLMPrometheusAsset) info.properties();
        assertEquals("http://prometheus", prom.prometheusUrl());
        assertEquals("user", prom.username());
        assertEquals(profile, prom.profile());

        JsonNode json = mapper.readTree(mapper.writeValueAsString(info));
        assertEquals("http://prometheus", json.get("prometheus-url").asText());
        assertEquals("user", json.get("username").asText());
        assertEquals("PROM_PASSWORD", json.get("password-key").asText());
        assertFalse(json.has("password"));
        // The asset itself only carries `PROM_PASSWORD`, the variable name — the password lives in the environment
        // and is read at scrape time, so there is nothing for the API to leak.
    }

    @Test
    void exposesStaticProfileWithoutConnectionFields() throws Exception
    {
        LLMStaticProfile profile = new LLMStaticProfile(LLMProvider.mistralai, "m", "FRA",
            new LLMStaticProfile.RequestProfile(500, 1000));
        AssetEntity entity = stamp(AssetEntity.from("env", new LLMStaticAsset(
            AssetIdentity.of("s", "env", "S"), profile)));

        AssetInfo info = AssetInfo.from(entity);

        assertInstanceOf(LLMStaticAsset.class, info.properties());
        assertEquals(profile, info.properties().profile());

        JsonNode json = mapper.readTree(mapper.writeValueAsString(info));
        assertEquals("s", json.get("id").asText());
        assertEquals("S", json.get("name").asText());
        assertEquals(LLMStaticModule.TYPE.name(), json.get("type").asText());
        assertFalse(json.has("prometheus-url"));
        assertFalse(json.has("namespace"));
        assertFalse(json.has("config-path"));
    }

    @Test
    void serializesDynamicModuleAssetTransparently() throws Exception
    {
        record DynamicProfile(String id, String param) implements AssetProfile {}

        @com.fasterxml.jackson.annotation.JsonTypeName("tech.illuin.custom")
        record DynamicAsset(
            @JsonUnwrapped AssetIdentity identity,
            @JsonProperty("profile") DynamicProfile profile,
            @JsonProperty("custom-field") String customField
        ) implements Asset
        {
            @Override
            public AssetType type()
            {
                return AssetType.of("tech.illuin", "custom", ActivityRegime.MEASURED, ServiceFamily.LLM);
            }
        }

        mapper.registerSubtypes(new NamedType(DynamicAsset.class, "tech.illuin.custom"));

        AssetEntity entity = stamp(AssetEntity.from("env", new DynamicAsset(AssetIdentity.of("dyn1", "env", "Dynamic"), new DynamicProfile("prof-1", "test-val"), "custom-value")));
        AssetInfo info = AssetInfo.from(entity);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(info));
        assertEquals("dyn1", json.get("id").asText());
        assertEquals("Dynamic", json.get("name").asText());
        assertEquals("tech.illuin.custom", json.get("type").asText());
        assertEquals("custom-value", json.get("custom-field").asText());
        assertEquals("prof-1", json.get("profile").get("id").asText());
        assertEquals("test-val", json.get("profile").get("param").asText());
        assertEquals("uuid", json.get("uuid").asText());
    }

    @Test
    void serializesUnrecognizedAssetSafely() throws Exception
    {
        String rawJson = "{\"id\":\"unrec-1\",\"environment-id\":\"env\",\"name\":\"Unknown Asset\",\"type\":\"tech.illuin.unknown\",\"custom\":\"value\"}";
        tech.illuin.wombat.context.model.UnrecognizedAsset unrecognized = new tech.illuin.wombat.context.model.UnrecognizedAsset(AssetIdentity.of("unrec-1", "env", "Unknown Asset"), "tech.illuin.unknown", rawJson);
        AssetEntity entity = new AssetEntity();
        entity.id = "unrec-1";
        entity.environmentId = "env";
        entity.name = "Unknown Asset";
        entity.type = "tech.illuin.unknown";
        entity.properties = unrecognized;
        entity = stamp(entity);

        AssetInfo info = AssetInfo.from(entity);
        assertEquals(unrecognized, info.properties());
        assertEquals("tech.illuin.unknown", info.type());
        assertEquals("tech.illuin.wombat-core.unknown", info.properties().type().name());
        assertEquals(ActivityRegime.UNKNOWN, info.properties().type().regime());
        assertEquals(ServiceFamily.UNKNOWN, info.properties().type().family());
        assertEquals("unknown", info.properties().profile().id());

        JsonNode json = mapper.readTree(mapper.writeValueAsString(info));
        assertEquals("unrec-1", json.get("id").asText());
        assertEquals("Unknown Asset", json.get("name").asText());
        assertEquals("tech.illuin.unknown", json.get("type").asText());
        assertEquals("uuid", json.get("uuid").asText());
    }

    private static AssetEntity stamp(AssetEntity entity)
    {
        entity.uuid = "uuid";
        entity.createdAt = Instant.ofEpochMilli(1);
        entity.updatedAt = Instant.ofEpochMilli(2);
        return entity;
    }
}
