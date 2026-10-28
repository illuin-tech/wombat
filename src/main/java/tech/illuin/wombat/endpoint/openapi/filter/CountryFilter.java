package tech.illuin.wombat.endpoint.openapi.filter;

import io.quarkus.smallrye.openapi.OpenApiFilter;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.media.Schema;

import java.util.List;

@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD)
public class CountryFilter implements OASFilter
{
    @Override
    public void filterOpenAPI(OpenAPI openAPI)
    {
        if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null)
            return;

        Schema externalClassSchema = openAPI.getComponents().getSchemas().get("Country");
        if (externalClassSchema != null)
        {
            externalClassSchema.setType(List.of(Schema.SchemaType.STRING));
            externalClassSchema.setProperties(null);
            externalClassSchema.setExample("FRA");
        }
    }
}
