package tech.illuin.wombat.impact.controller;

import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import tech.illuin.wombat.commons.response.Response;
import tech.illuin.wombat.core.asset.SimulatedAsset;
import tech.illuin.wombat.core.evaluation.AssetEvaluation;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.evaluation.WombatEvaluationException;

import java.util.ArrayList;
import java.util.List;

import static tech.illuin.wombat.impact.controller.SimulationRequest.Scope.COST;
import static tech.illuin.wombat.impact.controller.SimulationRequest.Scope.IMPACT;

@Path("simulation")
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
