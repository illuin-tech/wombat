package tech.illuin.wombat.persistence.push;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.activity.commons.Country;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.core.source.data.LLMData;
import tech.illuin.wombat.core.source.data.MetricData;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MetricPushJsonTest
{
    private static final ObjectMapper MAPPER = MetricDataJacksonCustomizer.register(JsonMapper.builder().findAndAddModules().build());

    private static final KubernetesData KUBERNETES = new KubernetesData("api", "cluster", "namespace", "pod", 100.0, 2048.0);
    private static final LLMData LLM = new LLMData("chat", LLMProvider.mistralai, "mistral-large-latest", Country.ofCode("FRA").orElseThrow(), 120L, 1L);

    @Test
    void metricData_isTaggedWithItsFamily() throws Exception
    {
        JsonNode json = MAPPER.valueToTree(push(KUBERNETES, LLM));

        assertEquals("KUBERNETES_CONTAINER", json.at("/metrics/0/family").asText());
        assertEquals("LLM", json.at("/metrics/1/family").asText());
        assertFalse(json.at("/metrics/0").has("serviceFamily"));
    }

    @Test
    void metricData_roundTripsToItsVariant() throws Exception
    {
        MetricPush push = push(KUBERNETES, LLM);

        assertEquals(push, MAPPER.readValue(MAPPER.writeValueAsString(push), MetricPush.class));
    }

    @Test
    void familyNames_matchTheServiceFamilyOfEachVariant() throws Exception
    {
        for (JsonSubTypes.Type type : MetricDataMixin.class.getAnnotation(JsonSubTypes.class).value())
        {
            MetricData sample = List.of(KUBERNETES, LLM).stream().filter(type.value()::isInstance).findFirst().orElseThrow();
            assertEquals(sample.serviceFamily().name(), type.name());
        }
    }

    private static MetricPush push(MetricData... metrics)
    {
        return new MetricPush(AssetIdentity.of("asset", "env", "Asset"), "tech.illuin.wombat-module.kubernetes-api", List.of(metrics));
    }
}
