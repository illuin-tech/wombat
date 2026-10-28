package tech.illuin.wombat.endpoint.openapi.surrogate;

import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import tech.illuin.wombat.core.evaluation.cost.commons.AssetCost;
import tech.illuin.wombat.core.evaluation.impact.commons.AssetImpact;

@Schema(
    discriminatorProperty = "type",
    discriminatorMapping = {
        @DiscriminatorMapping(value = "IMPACT", schema = AssetImpact.class),
        @DiscriminatorMapping(value = "COST", schema = AssetCost.class)
    },
    oneOf = { AssetImpact.class, AssetCost.class }
)
public interface AssetEvaluationSchema
{
}
