package tech.illuin.wombat.endpoint.openapi.filter;

import io.quarkus.smallrye.openapi.OpenApiFilter;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.media.Schema;

import java.util.List;

/**
 * Describes a {@link java.util.Currency} as Jackson serves it: its ISO 4217 code, not the bean the scanner sees.
 */
@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD)
public class CurrencyFilter implements OASFilter
{
    @Override
    public void filterOpenAPI(OpenAPI openAPI)
    {
        if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null)
            return;

        Schema externalClassSchema = openAPI.getComponents().getSchemas().get("Currency");
        if (externalClassSchema != null)
        {
            externalClassSchema.setType(List.of(Schema.SchemaType.STRING));
            externalClassSchema.setProperties(null);
            externalClassSchema.setExamples(List.of("EUR"));
        }
    }
}
