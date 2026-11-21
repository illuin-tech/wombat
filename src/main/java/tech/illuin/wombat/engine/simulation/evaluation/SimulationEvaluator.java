package tech.illuin.wombat.engine.simulation.evaluation;

import com.fasterxml.jackson.databind.json.JsonMapper;
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
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.evaluation.AssetEvaluation;
import tech.illuin.wombat.core.evaluation.AssetEvaluation.EvaluationType;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.evaluation.WombatEvaluationException;
import tech.illuin.wombat.core.evaluation.impact.commons.AssetImpact;
import tech.illuin.wombat.core.evaluation.impact.commons.Footprint;
import tech.illuin.wombat.engine.context.asset.AssetDefinitionReader;
import tech.illuin.wombat.engine.module.runtime.CoreDeployment;
import tech.illuin.wombat.engine.module.runtime.CoreRuntime;
import tech.illuin.wombat.engine.simulation.SimulationPeriod;
import tech.illuin.wombat.engine.simulation.evaluation.SimulationEvaluation.AssetResult;
import tech.illuin.wombat.engine.simulation.evaluation.SimulationEvaluation.AssetStatus;
import tech.illuin.wombat.engine.simulation.evaluation.SimulationEvaluation.Scaled;
import tech.illuin.wombat.engine.simulation.evaluation.SimulationEvaluation.Window;
import tech.illuin.wombat.engine.simulation.persistence.SimulationAssetEntity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
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
 * A copy's activity is resolved under its parent's identity, which is how it finds the parent's metrics; its impact
 * and cost under its own, so that every result names the simulation asset. The reference window is evaluated once,
 * then scaled linearly to every period.
 */
@ApplicationScoped
public class SimulationEvaluator
{
    private final CoreRuntime runtime;
    private final AssetDefinitionReader reader;
    private final JsonMapper mapper;
    private final Clock clock;
    private final ExecutorService executor;

    private static final Logger logger = LoggerFactory.getLogger(SimulationEvaluator.class);

    @Inject
    public SimulationEvaluator(CoreRuntime runtime, AssetDefinitionReader reader, JsonMapper mapper)
    {
        this(runtime, reader, mapper, Clock.systemUTC());
    }

    SimulationEvaluator(CoreRuntime runtime, AssetDefinitionReader reader, JsonMapper mapper, Clock clock)
    {
        this.runtime = runtime;
        this.reader = reader;
        this.mapper = mapper;
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

        List<AssetEvaluation> evaluated = results.stream()
            .filter(result -> result.status() == AssetStatus.OK)
            .flatMap(result -> result.result().stream())
            .toList();
        return new SimulationEvaluation(window, scale(evaluated, period, scopes), results);
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
            /* Stored under the simulation asset's identity */
            Asset evaluated = asset.properties;
            Asset resolved = asset.parentAssetId == null
                ? evaluated
                : this.reader.read(AssetIdentity.of(asset.parentAssetId, asset.parentEnvironmentId, asset.type), this.mapper.valueToTree(evaluated));

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
     * The standardized part of the evaluations over {@code window}, scaled to each period.
     */
    static Map<SimulationPeriod, Scaled> scale(List<AssetEvaluation> evaluations, SimulationPeriod window, Set<EvaluationType> scopes)
    {
        Footprint footprint = scopes.contains(IMPACT)
            ? Footprint.sum(evaluations.stream()
                .filter(AssetImpact.class::isInstance)
                .map(evaluation -> ((AssetImpact) evaluation).footprint())
                .filter(Objects::nonNull)
                .toList())
            : null;

        Map<SimulationPeriod, Scaled> scaled = new EnumMap<>(SimulationPeriod.class);
        for (SimulationPeriod period : SimulationPeriod.values())
        {
            double factor = (double) period.duration().toSeconds() / window.duration().toSeconds();
            /* A cost carries no amount yet */
            scaled.put(period, new Scaled(footprint != null ? footprint.multiply(factor) : null, null));
        }
        return scaled;
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
}
