package tech.illuin.wombat.endpoint.openapi.filter;

import io.quarkus.smallrye.openapi.OpenApiFilter;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.media.Schema;

import java.util.Map;

/**
 * Describes a simulation's results ({@code AssetResult.result}) as evaluations, told apart by their {@code type}
 * ({@code AssetEvaluationSchema}). Scanned from the core's {@code AssetEvaluation} interface, each would be an object
 * holding that type alone.
 */
@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD)
public class AssetEvaluationFilter implements OASFilter
{
    @Override
    public void filterOpenAPI(OpenAPI openAPI)
    {
        if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null)
            return;

        Map<String, Schema> schemas = openAPI.getComponents().getSchemas();
        Schema result = schemas.containsKey("AssetResult") && schemas.get("AssetResult").getProperties() != null
            ? schemas.get("AssetResult").getProperties().get("result")
            : null;
        if (result != null && schemas.containsKey("AssetEvaluationSchema"))
            result.setItems(OASFactory.createSchema().ref("AssetEvaluationSchema"));
    }
}
