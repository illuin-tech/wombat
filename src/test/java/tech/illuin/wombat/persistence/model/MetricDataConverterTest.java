package tech.illuin.wombat.persistence.model;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.core.source.data.LLMData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unlike the assets column, each metric table is homogeneous, so its converter binds a concrete type and the stored
 * JSON carries no type discriminator. These also pin the key names the native queries address by hand.
 */
class MetricDataConverterTest
{

    private final KubernetesDataConverter kubernetes = new KubernetesDataConverter();
    private final LLMDataConverter llm = new LLMDataConverter();

    @Test
    void kubernetesData_survivesARoundTrip()
    {
        KubernetesData data = new KubernetesData("activemq", "illuin.sandbox", "sandbox-bot",
            "illuin-bot-activemq-1", 2.65E7, 7.4E8);

        assertEquals(data, this.kubernetes.convertToEntityAttribute(this.kubernetes.convertToDatabaseColumn(data)));
    }

    @Test
    void llmData_survivesARoundTrip()
    {
        LLMData data = new LLMData("mistral-large-latest", "mistral-large-latest", 1234L);

        assertEquals(data, this.llm.convertToEntityAttribute(this.llm.convertToDatabaseColumn(data)));
    }

    @Test
    void storedJson_usesTheKeysTheNativeQueriesAddress()
    {
        // KubernetesMetricRepository groups on `serviceId` and reads back `cluster`/`namespace`;
        // LLMModelMetricRepository narrows to some of an asset's models on `model`.
        // A rename here silently breaks those queries, which have no compiler to catch it.
        String kubernetesJson = this.kubernetes.convertToDatabaseColumn(new KubernetesData(
            "svc", "cluster-1", "ns", "pod", 1.0, 2.0));
        assertContainsKey(kubernetesJson, "serviceId");
        assertContainsKey(kubernetesJson, "cluster");
        assertContainsKey(kubernetesJson, "namespace");

        String llmJson = this.llm.convertToDatabaseColumn(new LLMData("svc", "model", 1L));
        assertContainsKey(llmJson, "serviceId");
        assertContainsKey(llmJson, "model");
    }

    /**
     * The asset a sample belongs to lives in the tables' own columns, not the payload. Writing it back into the JSON
     * would restore exactly what the columns replaced: an unconstrained, un-indexable second copy free to drift from
     * the first.
     */
    @Test
    void storedJson_carriesNoAssetIdentity()
    {
        String kubernetesJson = this.kubernetes.convertToDatabaseColumn(new KubernetesData(
            "svc", "cluster-1", "ns", "pod", 1.0, 2.0));
        String llmJson = this.llm.convertToDatabaseColumn(new LLMData("svc", "model", 1L));

        for (String json : new String[] { kubernetesJson, llmJson })
        {
            assertLacksKey(json, "assetId");
            assertLacksKey(json, "environmentId");
            assertLacksKey(json, "assetName");
            assertLacksKey(json, "assetType");
        }
    }

    @Test
    void storedJson_carriesNoTypeDiscriminator()
    {
        String json = this.kubernetes.convertToDatabaseColumn(new KubernetesData(
            "svc", "cluster-1", "ns", "pod", 1.0, 2.0));

        assertFalse(json.contains("\"type\""), json);
    }

    @Test
    void nullsPassThroughUntouched()
    {
        assertNull(this.kubernetes.convertToDatabaseColumn(null));
        assertNull(this.kubernetes.convertToEntityAttribute(null));
        assertNull(this.llm.convertToEntityAttribute("  "));
    }

    private static void assertContainsKey(String json, String key)
    {
        assertTrue(json.contains("\"" + key + "\":"), key + " missing from " + json);
    }

    private static void assertLacksKey(String json, String key)
    {
        assertFalse(json.contains("\"" + key + "\":"), key + " should not be stored in " + json);
    }
}
