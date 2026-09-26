package tech.illuin.wombat.context.persistence;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.context.model.UnrecognizedAsset;
import tech.illuin.wombat.core.asset.ActivityRegime;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.ServiceFamily;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.asset.profile.ServerProvider;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.module.WombatModuleConfig;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIAsset;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIModule;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIServerProfile;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusAsset;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusModule;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusProfile;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;
import tech.illuin.wombat.module.llm_static.LLMStaticProfile;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AssetConverterTest
{
    private final AssetConverter converter = converter();

    @Test
    void kubernetesAsset_survivesARoundTrip()
    {
        KubernetesAPIAsset asset = new KubernetesAPIAsset(
            "k", "env", "K", "/kube/config", "ns", Optional.of("ctx"), Optional.of(Duration.ofSeconds(5)), 3,
            new KubernetesAPIServerProfile(ServerProvider.aws, "c5.large", "FRA", 43800));

        assertEquals(asset, this.roundTrip(asset));
    }

    @Test
    void llmStaticAsset_survivesARoundTrip()
    {
        LLMStaticAsset asset = new LLMStaticAsset("s", "env", "S",
            new LLMStaticProfile(LLMProvider.mistralai, "m", "FRA", new LLMStaticProfile.RequestProfile(500, 1000)));

        assertEquals(asset, this.roundTrip(asset));
    }

    @Test
    void llmPrometheusAsset_survivesARoundTrip()
    {
        LLMPrometheusAsset asset = new LLMPrometheusAsset("p", "env", "P",
            "http://prometheus", "http://proxy:8080", "user", "PROM_PASSWORD", 7,
            new LLMPrometheusProfile(LLMProvider.mistralai, "m", "FRA", new LLMPrometheusProfile.DynamicProfile("q")));

        assertEquals(asset, this.roundTrip(asset));
    }

    @Test
    void storedJson_carriesTheAssetTypeDiscriminator()
    {
        LLMStaticAsset asset = new LLMStaticAsset("s", "env", "S",
            new LLMStaticProfile(LLMProvider.mistralai, "m", "FRA", new LLMStaticProfile.RequestProfile(500, 1000)));

        String json = this.converter.convertToDatabaseColumn(asset);

        assertTrue(json.contains("\"type\":\"" + LLMStaticModule.TYPE.name() + "\""), json);
    }

    @Test
    void unrecognizedAsset_deserializesSafelyAndPreservesRawJson()
    {
        String rawJson = "{\"type\":\"tech.illuin.dropped.Asset\",\"id\":\"unrec-1\",\"environment-id\":\"env-1\",\"name\":\"Dropped Module Asset\",\"extraField\":\"keep-me\"}";

        Asset parsed = this.converter.convertToEntityAttribute(rawJson);
        assertInstanceOf(UnrecognizedAsset.class, parsed);

        UnrecognizedAsset unrecognized = (UnrecognizedAsset) parsed;
        assertEquals("unrec-1", unrecognized.id());
        assertEquals("env-1", unrecognized.environmentId());
        assertEquals("Dropped Module Asset", unrecognized.name());
        assertEquals("tech.illuin.dropped.Asset", unrecognized.rawType());
        assertEquals(rawJson, unrecognized.rawJson());
        assertEquals("tech.illuin.wombat-core.unknown", unrecognized.type().name());
        assertEquals(ActivityRegime.UNKNOWN, unrecognized.type().regime());
        assertEquals(ServiceFamily.UNKNOWN, unrecognized.type().family());
        assertEquals("unknown", unrecognized.profile().id());

        String serialized = this.converter.convertToDatabaseColumn(unrecognized);
        assertEquals(rawJson, serialized);
    }

    @Test
    void nullsPassThroughUntouched()
    {
        assertNull(this.converter.convertToDatabaseColumn(null));
        assertNull(this.converter.convertToEntityAttribute(null));
        assertNull(this.converter.convertToEntityAttribute("  "));
    }

    private Asset roundTrip(Asset asset)
    {
        return this.converter.convertToEntityAttribute(this.converter.convertToDatabaseColumn(asset));
    }

    private static AssetConverter converter()
    {
        AssetConverter converter = new AssetConverter();
        converter.mapper = new WombatModuleConfig().provideJsonMapper(modules());
        return converter;
    }

    private static List<WombatModule> modules()
    {
        return List.of(
            new KubernetesAPIModule(),
            new LLMPrometheusModule(),
            new LLMStaticModule()
        );
    }
}
