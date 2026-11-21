package tech.illuin.wombat.endpoint.openapi.filter;

import io.quarkus.smallrye.openapi.OpenApiFilter;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Describes the evaluations as they are served ({@code AssetEvaluationTypeCustomizer}): each one carries its
 * {@code type}, and a reference to the core's {@code AssetEvaluation} interface, e.g. in a simulation's results, is one
 * of them, told apart by that type ({@code AssetEvaluationSchema}).
 */
@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD)
public class AssetEvaluationFilter implements OASFilter
{
    private static final Map<String, String> TYPES = Map.of("AssetImpact", "IMPACT", "AssetCost", "COST");

    @Override
    public void filterOpenAPI(OpenAPI openAPI)
    {
        if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null)
            return;

        Map<String, Schema> schemas = openAPI.getComponents().getSchemas();
        TYPES.forEach((name, type) -> {
            Schema schema = schemas.get(name);
            if (schema != null)
                schema.addProperty("type", OASFactory.createSchema().addType(Schema.SchemaType.STRING).enumeration(List.of(type)).readOnly(true)).addRequired("type");
        });
        /* Scanned from the interface, it would be an object with no properties */
        if (schemas.containsKey("AssetEvaluation") && schemas.containsKey("AssetEvaluationSchema"))
            openAPI.getComponents().addSchema("AssetEvaluation", OASFactory.createSchema().ref("AssetEvaluationSchema"));
    }
}
