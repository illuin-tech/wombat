package tech.illuin.wombat.endpoint;

import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import tech.illuin.wombat.endpoint.openapi.surrogate.AssetEvaluationSchema;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.core.asset.SimulatedAsset;
import tech.illuin.wombat.core.evaluation.AssetEvaluation;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.evaluation.WombatEvaluationException;
import tech.illuin.wombat.endpoint.response.Status;

import java.util.ArrayList;
import java.util.List;

import static tech.illuin.wombat.endpoint.SimulationRequest.Scope.COST;
import static tech.illuin.wombat.endpoint.SimulationRequest.Scope.IMPACT;

@Path("api/simulation")
public class SimulationController
{
    private final AssetEvaluator evaluator;

    public SimulationController(AssetEvaluator evaluator)
    {
        this.evaluator = evaluator;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(
        responseCode = "200",
        content = @Content(
            mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(
                properties = {
                    @SchemaProperty(name = "status", implementation = Status.class),
                    @SchemaProperty(name = "payload", type = SchemaType.ARRAY, implementation = AssetEvaluationSchema.class)
                }
            )
        )
    )
    public Response<List<AssetEvaluation>> simulate(@Valid SimulationRequest request)
    {
        try {
            if (request == null)
                return Response.success(List.of());

            List<AssetEvaluation> evaluations = new ArrayList<>();
            for (SimulationRequest.SimulatedAsset assetPayload : request.assets())
            {
                if (assetPayload == null)
                    continue;

                SimulatedAsset asset = assetPayload.toSimulatedAsset();
                if (request.scopes().contains(IMPACT))
                    this.evaluator.computeImpact(asset, asset.activity()).ifPresent(evaluations::add);
                if (request.scopes().contains(COST))
                    this.evaluator.computeCost(asset, asset.activity()).ifPresent(evaluations::add);
            }

            return Response.success(evaluations);
        }
        catch (WombatEvaluationException e) {
            throw new InternalServerErrorException(e);
        }
    }
}
