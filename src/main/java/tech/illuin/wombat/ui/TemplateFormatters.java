package tech.illuin.wombat.ui;

import io.quarkus.qute.TemplateExtension;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIAsset;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@TemplateExtension
public class TemplateFormatters
{

    private static final DateTimeFormatter LOCAL_DT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(ZoneOffset.UTC);

    public static String asLocalDateTime(Instant instant)
    {
        if (instant == null) return "";
        if (instant.equals(Instant.MIN) || instant.equals(Instant.MAX)) return "";
        return LOCAL_DT.format(instant);
    }

    public static String asIsoUtc(Instant instant)
    {
        if (instant == null || instant.equals(Instant.MIN) || instant.equals(Instant.MAX)) return "";
        return instant.toString();
    }

    public static String asPercent(Double value)
    {
        if (value == null) return "—";
        return String.format(Locale.US, "%.2f%%", value * 100);
    }

    public static String namespace(Asset asset)
    {
        if (asset instanceof KubernetesAPIAsset kubernetes) return kubernetes.namespace();
        return "";
    }

    public static String asDecimal(Float value)
    {
        if (value == null) return "—";
        return asDecimal(value.doubleValue());
    }

    public static String asDecimal(Double value)
    {
        if (value == null) return "—";
        if (value != 0.0 && Math.abs(value) < 0.005)
        {
            return String.format(Locale.US, "%.2E", value)
                .replaceAll("\\.?0+(E)", "$1")
                .replaceAll("E([+-])0*(\\d+)", "E$1$2");
        }
        return String.format(Locale.US, "%.2f", value).replaceAll("\\.?0+$", "");
    }
}
