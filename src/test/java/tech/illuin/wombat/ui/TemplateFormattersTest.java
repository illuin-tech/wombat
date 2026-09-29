package tech.illuin.wombat.ui;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.asset.profile.ServerProvider;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIAsset;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIServerProfile;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticProfile;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateFormattersTest
{

    @Test
    void asLocalDateTime_formatsInstant()
    {
        assertEquals("2026-06-04T13:30", TemplateFormatters.asLocalDateTime(Instant.parse("2026-06-04T13:30:00Z")));
    }

    @Test
    void asLocalDateTime_nullOrSentinel_returnsEmpty()
    {
        assertEquals("", TemplateFormatters.asLocalDateTime(null));
        assertEquals("", TemplateFormatters.asLocalDateTime(Instant.MIN));
        assertEquals("", TemplateFormatters.asLocalDateTime(Instant.MAX));
    }

    @Test
    void asIsoUtc_returnsIsoString()
    {
        assertEquals("2026-06-04T13:30:00Z", TemplateFormatters.asIsoUtc(Instant.parse("2026-06-04T13:30:00Z")));
    }

    @Test
    void asPercent_multipliesByHundredAndAppendsSymbol()
    {
        assertEquals("42.50%", TemplateFormatters.asPercent(0.425d));
        assertEquals("0.00%", TemplateFormatters.asPercent(0.0d));
    }

    @Test
    void asPercent_null_returnsDash()
    {
        assertEquals("—", TemplateFormatters.asPercent(null));
    }

    @Test
    void asDecimal_largeValue_formattedWithTwoDecimals()
    {
        assertEquals("3.14", TemplateFormatters.asDecimal(3.14f));
    }

    @Test
    void asDecimal_trailingZerosTrimmed()
    {
        assertEquals("5", TemplateFormatters.asDecimal(5.0f));
    }

    @Test
    void asDecimal_verySmallValue_usesScientificNotation()
    {
        String result = TemplateFormatters.asDecimal(0.0001f);
        assertEquals("1E-4", result);
    }

    @Test
    void asDecimal_null_returnsDash()
    {
        assertEquals("—", TemplateFormatters.asDecimal((Float) null));
        assertEquals("—", TemplateFormatters.asDecimal((Double) null));
    }

    @Test
    void namespace_kubernetesAsset_returnsItsNamespace()
    {
        KubernetesAPIAsset asset = new KubernetesAPIAsset(
            AssetIdentity.of("k", "env", "K"), "/kube/config", "ns", Optional.empty(), Optional.empty(), 0,
            new KubernetesAPIServerProfile(ServerProvider.aws, "c5.large", "FRA", 43800));

        assertEquals("ns", TemplateFormatters.namespace(asset));
    }

    @Test
    void namespace_nonKubernetesAsset_returnsEmpty()
    {
        LLMStaticAsset asset = new LLMStaticAsset(AssetIdentity.of("s", "env", "S"),
            new LLMStaticProfile(LLMProvider.mistralai, "m", "FRA", new LLMStaticProfile.RequestProfile(500, 1000)));

        assertEquals("", TemplateFormatters.namespace(asset));
    }
}
