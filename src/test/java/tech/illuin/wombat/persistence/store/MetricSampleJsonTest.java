package tech.illuin.wombat.persistence.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.activity.commons.Country;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.core.source.data.LLMData;
import tech.illuin.wombat.core.source.data.MetricData;import tech.illuin.wombat.persistence.backend.api.store.MetricSample;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MetricSampleJsonTest
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    private static final AssetIdentity ASSET = AssetIdentity.of("asset", "env", "Asset", "tech.illuin.wombat-module.kubernetes-api");
    private static final long WINDOW_START_MS = 1_700_000_100_000L;
    private static final long WINDOW_MS = 300_000L;

    private static final KubernetesData KUBERNETES = new KubernetesData("api", "cluster", "namespace", "pod", 100.0, 2048.0);
    private static final LLMData LLM = new LLMData("chat", LLMProvider.mistralai, "mistral-large-latest", Country.ofCode("FRA").orElseThrow(), 120L, 1L);

    @Test
    void metricData_isTaggedWithItsFamily()
    {
        JsonNode json = MAPPER.valueToTree(List.of(sample(KUBERNETES), sample(LLM)));

        assertEquals("KUBERNETES_CONTAINER", json.at("/0/data/family").asText());
        assertEquals("LLM", json.at("/1/data/family").asText());
        assertFalse(json.at("/0/data").has("serviceFamily"));
    }

    @Test
    void sample_carriesItsAssetTypeAndWindow()
    {
        JsonNode json = MAPPER.valueToTree(sample(KUBERNETES));

        assertEquals("tech.illuin.wombat-module.kubernetes-api", json.at("/asset/type").asText());
        assertFalse(json.at("/window/start").isMissingNode());
        assertFalse(json.at("/window/end").isMissingNode());
        assertFalse(json.has("instantMs"));
        assertFalse(json.has("windowMs"));
    }

    @Test
    void samples_roundTripToTheirVariant() throws Exception
    {
        List<MetricSample<MetricData>> samples = List.of(sample(KUBERNETES), sample(LLM));

        List<MetricSample<MetricData>> read = MAPPER.readValue(MAPPER.writeValueAsString(samples), new TypeReference<>() {});

        assertEquals(samples, read);
        assertEquals(WINDOW_START_MS, read.getFirst().instantMs());
        assertEquals(WINDOW_MS, read.getFirst().windowMs());
    }

    private static MetricSample<MetricData> sample(MetricData data)
    {
        return MetricSample.of(ASSET, WINDOW_START_MS, WINDOW_MS, data);
    }
}
