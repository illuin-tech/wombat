package tech.illuin.wombat.impact.controller;

import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.context.WombatContextProvider;
import tech.illuin.wombat.core.activity.commons.AssetFilter;
import tech.illuin.wombat.core.evaluation.WombatEvaluationException;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.evaluation.impact.commons.AssetImpact;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.activity.kubernetes.NoCPUUsageException;
import tech.illuin.wombat.commons.response.Response;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Path("impact")
public class ImpactController
{
    private final AssetEvaluator assetEvaluator;
    private final WombatContextProvider contextProvider;

    public ImpactController(AssetEvaluator assetEvaluator, WombatContextProvider contextProvider)
    {
        this.assetEvaluator = assetEvaluator;
        this.contextProvider = contextProvider;
    }

    @GET
    @Path("environments")
    @Produces(MediaType.APPLICATION_JSON)
    public Response<List<Environment>> getEnvironments()
    {
        List<Environment> environments = this.contextProvider.provide().environments();
        return Response.success(environments);
    }

    @GET
    @Path("environments/{environment}/assets")
    @Produces(MediaType.APPLICATION_JSON)
    public Response<List<EnvironmentDescription.AssetSummary>> getAssets(@PathParam("environment") String environment)
    {
        try {
            List<EnvironmentDescription.AssetSummary> assets = this.environment(environment).assets().stream()
                .map(EnvironmentDescription.AssetSummary::from)
                .toList();
            return Response.success(assets);
        }
        catch (UnknownEnvironmentException e) {
            throw new NotFoundException(e);
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response<List<AssetImpact>> getImpact(@Valid ImpactRequest request)
    {
        try {
            TimeRange timeRange = request.sourceTimeRange() == null ? currentMonthTimeRange() : request.sourceTimeRange();
            AssetFilter scope = scope(request.environments());

            List<AssetImpact> assetImpacts = this.calculate(timeRange, scope);

            if (assetImpacts.isEmpty())
                throw new BadRequestException("No assets matched scope " + scope.environments());
            return Response.success(assetImpacts);
        }
        catch (WombatEvaluationException e) {
            throw new InternalServerErrorException(e);
        }
        catch (NoCPUUsageException e) {
            throw new BadRequestException("No CPU Usage Could be found", e);
        }
    }

    private List<AssetImpact> calculate(TimeRange timeRange, AssetFilter filter) throws NoCPUUsageException, WombatEvaluationException
    {
        try {
            return this.assetEvaluator.evaluate(timeRange, filter, AssetImpact.class);
        }
        catch (WombatEvaluationException e) {
            if (e.getCause() instanceof NoCPUUsageException cause)
                throw cause;
            throw e;
        }
    }

    private static AssetFilter scope(List<ImpactRequest.Environment> environments)
    {
        if (environments.isEmpty())
            return AssetFilter.none();

        return new AssetFilter(environments.stream()
            .map(ImpactController::environmentScope)
            .collect(Collectors.toSet()));
    }

    private static AssetFilter.Environment environmentScope(ImpactRequest.Environment environment)
    {
        if (environment.assets() == null)
            return new AssetFilter.Environment(environment.id(), Set.of());

        return new AssetFilter.Environment(environment.id(), environment.assets().stream()
            .map(a -> new AssetFilter.Asset(a.id(), a.serviceIds()))
            .collect(Collectors.toSet()));
    }

    private Environment environment(String environmentId) throws UnknownEnvironmentException
    {
        return this.contextProvider.provide().environments().stream()
            .filter(candidate -> candidate.id().equals(environmentId))
            .findFirst()
            .orElseThrow(() -> new UnknownEnvironmentException(environmentId));
    }

    private static TimeRange currentMonthTimeRange()
    {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        Instant start = now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant end = now.plusMonths(1).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
        return new TimeRange(start, end);
    }
}
