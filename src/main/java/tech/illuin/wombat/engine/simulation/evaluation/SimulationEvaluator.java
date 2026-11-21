package tech.illuin.wombat.engine.simulation.evaluation;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.activity.WombatActivityException;
import tech.illuin.wombat.core.activity.commons.ActivityData;
import tech.illuin.wombat.core.activity.commons.AssetActivity;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.activity.kubernetes.NoCPUUsageException;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.evaluation.AssetEvaluation;
import tech.illuin.wombat.core.evaluation.AssetEvaluation.EvaluationType;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.evaluation.WombatEvaluationException;
import tech.illuin.wombat.core.evaluation.cost.commons.AssetCost;
import tech.illuin.wombat.core.evaluation.cost.commons.Money;
import tech.illuin.wombat.core.evaluation.cost.commons.ServiceCost;
import tech.illuin.wombat.core.evaluation.impact.commons.AssetImpact;
import tech.illuin.wombat.core.evaluation.impact.commons.Footprint;
import tech.illuin.wombat.core.evaluation.impact.commons.ServiceImpact;
import tech.illuin.wombat.engine.module.runtime.CoreDeployment;
import tech.illuin.wombat.engine.module.runtime.CoreRuntime;
import tech.illuin.wombat.engine.simulation.SimulationAssetService;
import tech.illuin.wombat.engine.simulation.SimulationPeriod;
import tech.illuin.wombat.engine.simulation.evaluation.SimulationEvaluation.AssetResult;
import tech.illuin.wombat.engine.simulation.evaluation.SimulationEvaluation.AssetStatus;
import tech.illuin.wombat.engine.simulation.evaluation.SimulationEvaluation.Scaled;
import tech.illuin.wombat.engine.simulation.evaluation.SimulationEvaluation.ScaledService;
import tech.illuin.wombat.engine.simulation.evaluation.SimulationEvaluation.Window;
import tech.illuin.wombat.engine.simulation.persistence.SimulationAssetEntity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static tech.illuin.wombat.core.evaluation.AssetEvaluation.EvaluationType.COST;
import static tech.illuin.wombat.core.evaluation.AssetEvaluation.EvaluationType.IMPACT;

/**
 * Evaluates the assets of a simulation on demand; nothing is cached nor stored. Each asset gets an evaluator of its
 * own, built from the running core and closed once done, so that the components it builds never serve the dashboard,
 * nor another asset.
 * <p>
 * A copy's activity is resolved under its source's identity, which is how it finds the source's metrics; its impact
 * and cost under its own (see {@link SimulationAssetService#asset}). The reference window is evaluated once, then
 * scaled linearly to every period, here only.
 */
@ApplicationScoped
public class SimulationEvaluator
{
    private final CoreRuntime runtime;
    private final SimulationAssetService assets;
    private final Clock clock;
    private final ExecutorService executor;

    private static final Logger logger = LoggerFactory.getLogger(SimulationEvaluator.class);

    @Inject
    public SimulationEvaluator(CoreRuntime runtime, SimulationAssetService assets)
    {
        this(runtime, assets, Clock.systemUTC());
    }

    SimulationEvaluator(CoreRuntime runtime, SimulationAssetService assets, Clock clock)
    {
        this.runtime = runtime;
        this.assets = assets;
        this.clock = clock;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * @param period the reference window: the trailing one ending at the current hour for a day, at the current day
     *               otherwise (UTC)
     */
    public SimulationEvaluation evaluate(String simulationId, List<SimulationAssetEntity> assets, SimulationPeriod period, Set<EvaluationType> scopes)
    {
        /* Read once, so that a redeploy in between cannot split the evaluation over two cores */
        CoreDeployment deployment = this.runtime.current();
        Window window = this.window(period);

        List<CompletableFuture<AssetResult>> futures = assets.stream()
            .map(asset -> CompletableFuture.supplyAsync(() -> this.evaluate(simulationId, asset, deployment, window, scopes), this.executor))
            .toList();
        List<AssetResult> results = futures.stream()
            .map(CompletableFuture::join)
            .toList();

        return new SimulationEvaluation(window, factors(period), totals(results, scopes), results);
    }

    @PreDestroy
    void close()
    {
        this.executor.close();
    }

    Window window(SimulationPeriod period)
    {
        Instant end = this.clock.instant().truncatedTo(period == SimulationPeriod.DAY ? ChronoUnit.HOURS : ChronoUnit.DAYS);
        return new Window(end.minus(period.duration()), end, period);
    }

    /* Any failure is the asset's, reported in its result rather than failing the whole simulation */
    @SuppressWarnings("IllegalCatch")
    private AssetResult evaluate(String simulationId, SimulationAssetEntity asset, CoreDeployment deployment, Window window, Set<EvaluationType> scopes)
    {
        if (deployment.types().stream().noneMatch(type -> type.name().equals(asset.type)))
            return failed(asset, AssetStatus.MODULE_DISABLED, "The running core has no module for asset-type " + asset.type);

        try (AssetEvaluator evaluator = deployment.newEvaluator()) {
            Asset evaluated = this.assets.asset(asset);
            Asset resolved = this.assets.activityAsset(asset);

            Optional<AssetActivity> activity = evaluator.computeActivity(resolved, new TimeRange(window.start(), window.end()), null);
            if (activity.isEmpty())
                return failed(asset, AssetStatus.NO_DATA, "No activity between " + window.start() + " and " + window.end());

            List<AssetEvaluation> result = new ArrayList<>();
            for (ActivityData data : activity.get().activity())
            {
                if (scopes.contains(IMPACT))
                    evaluator.computeImpact(evaluated, data).ifPresent(result::add);
                if (scopes.contains(COST))
                    evaluator.computeCost(evaluated, data).ifPresent(result::add);
            }
            return new AssetResult(asset.id, AssetStatus.OK, null, coverage(evaluator, resolved, window), result, scale(result, window.period(), scopes));
        }
        catch (WombatActivityException | WombatEvaluationException | RuntimeException e) {
            Optional<NoCPUUsageException> noData = noData(e);
            if (noData.isPresent())
                return failed(asset, AssetStatus.NO_DATA, noData.get().getMessage());
            logger.error("Could not evaluate asset {} of simulation {}: {}", asset.id, simulationId, e.getMessage(), e);
            return failed(asset, AssetStatus.ERROR, e.getMessage());
        }
    }

    /* The window split in hours for a day, in days otherwise, as metrics are kept at least that fine */
    private static double coverage(AssetEvaluator evaluator, Asset resolved, Window window) throws WombatActivityException
    {
        Duration resolution = window.period() == SimulationPeriod.DAY ? Duration.ofHours(1) : Duration.ofDays(1);
        long slots = window.period().duration().dividedBy(resolution);
        long covered = evaluator.computeActivity(resolved, new TimeRange(window.start(), window.end(), resolution), null)
            .map(activity -> activity.activity().stream()
                .map(data -> data.range().start())
                .distinct()
                .count()
            )
            .orElse(0L);
        return Math.min(1.0, (double) covered / slots);
    }

    /**
     * How many windows each period holds: a period's results are the window's times its factor.
     */
    static Map<SimulationPeriod, Double> factors(SimulationPeriod window)
    {
        Map<SimulationPeriod, Double> factors = new EnumMap<>(SimulationPeriod.class);
        for (SimulationPeriod period : SimulationPeriod.values())
            factors.put(period, (double) period.duration().toSeconds() / window.duration().toSeconds());
        return factors;
    }

    /**
     * The standardized part of one asset's evaluations over {@code window}, scaled to each period: its footprint and
     * cost, and each service's, summed over the evaluations.
     */
    static Map<SimulationPeriod, Scaled> scale(List<AssetEvaluation> evaluations, SimulationPeriod window, Set<EvaluationType> scopes)
    {
        List<AssetImpact> impacts = evaluations.stream()
            .filter(AssetImpact.class::isInstance)
            .map(AssetImpact.class::cast)
            .toList();
        List<AssetCost> costs = evaluations.stream()
            .filter(AssetCost.class::isInstance)
            .map(AssetCost.class::cast)
            .toList();
        Footprint footprint = scopes.contains(IMPACT)
            ? Footprint.sum(impacts.stream().map(AssetImpact::footprint).filter(Objects::nonNull).toList())
            : null;
        List<Money> cost = scopes.contains(COST)
            ? Money.sum(costs.stream().map(AssetCost::cost).filter(Objects::nonNull).toList())
            : null;
        List<ScaledService> services = scopes.isEmpty() ? null : services(impacts, costs, scopes);

        Map<SimulationPeriod, Scaled> scaled = new EnumMap<>(SimulationPeriod.class);
        factors(window).forEach((period, factor) -> scaled.put(period, new Scaled(
            footprint != null ? footprint.multiply(factor) : null,
            ScaledService.multiply(cost, factor),
            services != null ? services.stream().map(service -> service.multiply(factor)).toList() : null
        )));
        return scaled;
    }

    /**
     * The scaled results of the assets evaluated {@link AssetStatus#OK}, summed per period; zero without any.
     */
    static Map<SimulationPeriod, Scaled> totals(List<AssetResult> results, Set<EvaluationType> scopes)
    {
        Map<SimulationPeriod, Scaled> totals = new EnumMap<>(SimulationPeriod.class);
        for (SimulationPeriod period : SimulationPeriod.values())
        {
            List<Scaled> evaluated = results.stream()
                .filter(result -> result.status() == AssetStatus.OK)
                .map(result -> result.scaled().get(period))
                .toList();
            Footprint impact = scopes.contains(IMPACT)
                ? Footprint.sum(evaluated.stream().map(Scaled::impact).filter(Objects::nonNull).toList())
                : null;
            List<Money> cost = scopes.contains(COST)
                ? Money.sum(evaluated.stream().map(Scaled::cost).filter(Objects::nonNull).flatMap(List::stream).toList())
                : null;
            totals.put(period, new Scaled(impact, cost, null));
        }
        return totals;
    }

    /* One line per service, in the order met: an asset evaluated over several activities names a service in each */
    private static List<ScaledService> services(List<AssetImpact> impacts, List<AssetCost> costs, Set<EvaluationType> scopes)
    {
        Map<String, ServiceLine> lines = new LinkedHashMap<>();
        if (scopes.contains(IMPACT))
        {
            for (AssetImpact impact : impacts)
            {
                for (ServiceImpact service : impact.serviceImpacts())
                    lines.computeIfAbsent(service.serviceId(), id -> new ServiceLine(service.assetType())).add(service.footprint());
            }
        }
        if (scopes.contains(COST))
        {
            for (AssetCost cost : costs)
            {
                for (ServiceCost service : cost.serviceCosts())
                    lines.computeIfAbsent(service.serviceId(), id -> new ServiceLine(service.assetType())).add(service.cost());
            }
        }
        return lines.entrySet().stream()
            .map(line -> line.getValue().toScaled(line.getKey(), scopes))
            .toList();
    }

    private static AssetResult failed(SimulationAssetEntity asset, AssetStatus status, String message)
    {
        return new AssetResult(asset.id, status, message, null, List.of(), Map.of());
    }

    /* A cluster without CPU usage has no activity to evaluate; the exception comes wrapped, as it is checked */
    private static Optional<NoCPUUsageException> noData(Throwable error)
    {
        for (Throwable cause = error; cause != null; cause = cause.getCause())
        {
            if (cause instanceof NoCPUUsageException noCpuUsage)
                return Optional.of(noCpuUsage);
        }
        return Optional.empty();
    }

    /* What a service sums up to, over the evaluations naming it */
    private static final class ServiceLine
    {
        private final AssetType assetType;
        private final List<Footprint> footprints = new ArrayList<>();
        private final List<Money> costs = new ArrayList<>();

        ServiceLine(AssetType assetType)
        {
            this.assetType = assetType;
        }

        void add(Footprint footprint)
        {
            if (footprint != null)
                this.footprints.add(footprint);
        }

        void add(Money cost)
        {
            if (cost != null)
                this.costs.add(cost);
        }

        ScaledService toScaled(String serviceId, Set<EvaluationType> scopes)
        {
            return new ScaledService(
                serviceId,
                this.assetType,
                scopes.contains(IMPACT) && !this.footprints.isEmpty() ? Footprint.sum(this.footprints) : null,
                scopes.contains(COST) ? Money.sum(this.costs) : null
            );
        }
    }
}
