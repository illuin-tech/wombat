package tech.illuin.wombat.ui;

import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Filter;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import tech.illuin.wombat.core.asset.Asset;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class WombatPebbleExtension extends AbstractExtension
{
    @Override
    public Map<String, Filter> getFilters()
    {
        return Map.of(
            "decimal", filter(value -> TemplateFormatters.asDecimal(value == null ? null : ((Number) value).doubleValue())),
            "percent", filter(value -> TemplateFormatters.asPercent(value == null ? null : ((Number) value).doubleValue())),
            "isoUtc", filter(value -> TemplateFormatters.asIsoUtc((Instant) value)),
            "localDateTime", filter(value -> TemplateFormatters.asLocalDateTime((Instant) value)),
            "namespace", filter(value -> TemplateFormatters.namespace((Asset) value))
        );
    }

    private static Filter filter(Function<Object, String> formatter)
    {
        return new Filter()
        {
            @Override
            public List<String> getArgumentNames()
            {
                return List.of();
            }

            @Override
            public Object apply(Object input, Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber)
            {
                return formatter.apply(input);
            }
        };
    }
}
