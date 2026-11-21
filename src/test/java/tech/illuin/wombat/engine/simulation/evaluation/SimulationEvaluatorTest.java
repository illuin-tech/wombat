package tech.illuin.wombat.engine.simulation.evaluation;

import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.illuin.wombat.core.activity.WombatActivityException;
import tech.illuin.wombat.core.activity.commons.ActivityData;
import tech.illuin.wombat.core.activity.commons.AssetActivity;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.activity.kubernetes.NoCPUUsageException;
import tech.illuin.wombat.core.activity.llm.LLMActivityData;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.profile.AssetProfile;
import tech.illuin.wombat.core.asset.profile.LLMProvider;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.evaluation.AssetEvaluation;
import tech.illuin.wombat.core.evaluation.AssetEvaluation.EvaluationType;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.evaluation.cost.commons.AssetCost;
import tech.illuin.wombat.core.evaluation.cost.commons.Money;
import tech.illuin.wombat.core.evaluation.cost.commons.ServiceCost;
import tech.illuin.wombat.core.evaluation.impact.commons.AssetImpact;
import tech.illuin.wombat.core.evaluation.impact.commons.Footprint;
import tech.illuin.wombat.core.evaluation.impact.commons.Footprint.FootprintImpact;
import tech.illuin.wombat.core.evaluation.impact.commons.Footprint.FootprintImpact.FootprintImpactItem;
import tech.illuin.wombat.core.evaluation.impact.commons.ImpactProvider;
import tech.illuin.wombat.core.evaluation.impact.commons.ServiceImpact;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.engine.context.asset.AssetDefinitionReader;
import tech.illuin.wombat.engine.context.asset.AssetValidator;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.module.WombatModuleConfig;
import tech.illuin.wombat.engine.module.registry.ModuleRegistries;
import tech.illuin.wombat.engine.module.runtime.CoreDeployment;
import tech.illuin.wombat.engine.module.runtime.CoreRuntime;
import tech.illuin.wombat.engine.simulation.SimulationAssetService;
import tech.illuin.wombat.engine.simulation.SimulationPeriod;
import tech.illuin.wombat.engine.simulation.evaluation.SimulationEvaluation.AssetResult;
import tech.illuin.wombat.engine.simulation.evaluation.SimulationEvaluation.AssetStatus;
import tech.illuin.wombat.engine.simulation.evaluation.SimulationEvaluation.Scaled;
import tech.illuin.wombat.engine.simulation.evaluation.SimulationEvaluation.ScaledService;
import tech.illuin.wombat.engine.simulation.persistence.SimulationAssetEntity;
import tech.illuin.wombat.engine.simulation.persistence.SimulationAssetRepository;
import tech.illuin.wombat.engine.simulation.persistence.SimulationRepository;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticModule;
import tech.illuin.wombat.module.llm_static.LLMStaticProfile;
import tech.illuin.wombat.module.llm_static.scenario.ConstantScenario;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.illuin.wombat.core.activity.commons.Country.FRANCE;

class SimulationEvaluatorTest
{
    private static final String SIMULATION = "what-if";
    private static final String TYPE = LLMStaticModule.TYPE.name();
    private static final Instant NOW = Instant.parse("2026-11-21T10:37:12Z");
    private static final Set<EvaluationType> BOTH = EnumSet.allOf(EvaluationType.class);
    private static final JsonMapper MAPPER = new WombatModuleConfig().provideJsonMapper(List.of(new LLMStaticModule()));

    private CoreRuntime runtime;
    private List<AssetEvaluator> evaluators;
    private SimulationEvaluator simulations;
    /* How the resolvers of the next evaluators fail, if they do */
    private Failure failing;
    /* How many slots of the window's daily split have activity */
    private int coveredDays = 30;

    @BeforeEach
    void setUp()
    {
        /* The evaluator builds them from its virtual threads, one per asset */
        this.evaluators = new CopyOnWriteArrayList<>();
        this.runtime = mock(CoreRuntime.class);
        when(this.runtime.current()).thenReturn(new CoreDeployment(
            1, "id", List.of(new LLMStaticModule()), Set.of(), mock(AssetMonitor.class), mock(AssetEvaluator.class), this::newEvaluator, NOW
        ));
        SimulationAssetService assets = new SimulationAssetService(
            mock(SimulationAssetRepository.class), mock(SimulationRepository.class), mock(AssetRepository.class), reader(), MAPPER
        );
        this.simulations = new SimulationEvaluator(this.runtime, assets, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aCopy_resolvesItsActivityUnderItsSourcesIdentity_andItsImpactUnderItsOwn() throws Exception
    {
        SimulationEvaluation evaluation = this.simulations.evaluate(SIMULATION, List.of(copy("chatbot")), SimulationPeriod.MONTH, BOTH);

        AssetEvaluator evaluator = this.evaluators.getFirst();
        ArgumentCaptor<Asset> resolved = ArgumentCaptor.forClass(Asset.class);
        verify(evaluator, times(2)).computeActivity(resolved.capture(), any(), isNull());
        assertEquals(AssetIdentity.of("real-chatbot", "prod", TYPE), resolved.getValue().identity());
        verify(evaluator).computeImpact(argThat(asset -> asset.identity().equals(SimulationAssetService.identity(SIMULATION, "chatbot", TYPE))), any());
        assertEquals(AssetStatus.OK, evaluation.assets().getFirst().status());
    }

    @Test
    void anAssetWithoutSource_isResolvedUnderItsOwnIdentity() throws Exception
    {
        this.simulations.evaluate(SIMULATION, List.of(scratch("scratch")), SimulationPeriod.MONTH, BOTH);

        verify(this.evaluators.getFirst(), times(2)).computeActivity(
            argThat(asset -> asset.identity().equals(SimulationAssetService.identity(SIMULATION, "scratch", TYPE))), any(), isNull()
        );
    }

    @Test
    void anAssetWithoutActivity_hasNoData()
    {
        this.failing = new Failure.None();

        AssetResult result = this.simulations.evaluate(SIMULATION, List.of(scratch("scratch")), SimulationPeriod.MONTH, BOTH).assets().getFirst();

        assertEquals(AssetStatus.NO_DATA, result.status());
        assertTrue(result.message().contains("No activity"));
        assertNull(result.coverage());
        assertTrue(result.result().isEmpty());
    }

    @Test
    void aClusterWithoutCpuUsage_hasNoData()
    {
        this.failing = new Failure.Throws(new WombatActivityException("wrapped", new NoCPUUsageException("No CPU usage for test-cluster")));

        AssetResult result = this.simulations.evaluate(SIMULATION, List.of(scratch("scratch")), SimulationPeriod.MONTH, BOTH).assets().getFirst();

        assertEquals(AssetStatus.NO_DATA, result.status());
        assertEquals("No CPU usage for test-cluster", result.message());
    }

    @Test
    void anyOtherFailure_isTheAssetsError_andItsEvaluatorIsStillClosed()
    {
        this.failing = new Failure.Throws(new IllegalStateException("broken resolver"));

        SimulationEvaluation evaluation = this.simulations.evaluate(SIMULATION, List.of(scratch("a"), scratch("b")), SimulationPeriod.MONTH, BOTH);

        assertEquals(List.of(AssetStatus.ERROR, AssetStatus.ERROR), evaluation.assets().stream().map(AssetResult::status).toList());
        assertEquals("broken resolver", evaluation.assets().getFirst().message());
        assertEquals(2, this.evaluators.size());
        this.evaluators.forEach(evaluator -> verify(evaluator).close());
    }

    @Test
    void anAssetOfATypeNotDeployed_isModuleDisabled_withoutBuildingAnEvaluator()
    {
        SimulationAssetEntity gone = scratch("gone");
        gone.type = "acme.gone";

        AssetResult result = this.simulations.evaluate(SIMULATION, List.of(gone), SimulationPeriod.MONTH, BOTH).assets().getFirst();

        assertEquals(AssetStatus.MODULE_DISABLED, result.status());
        assertTrue(this.evaluators.isEmpty());
    }

    @Test
    void eachAssetGetsAnEvaluatorOfItsOwn_fromOneDeployment()
    {
        this.simulations.evaluate(SIMULATION, List.of(copy("a"), copy("b"), scratch("c")), SimulationPeriod.MONTH, BOTH);

        assertEquals(3, this.evaluators.size());
        this.evaluators.forEach(evaluator -> verify(evaluator).close());
        verify(this.runtime, times(1)).current();
    }

    @Test
    void onlyTheRequestedScopesAreEvaluated() throws Exception
    {
        AssetResult impact = this.simulations.evaluate(SIMULATION, List.of(scratch("a")), SimulationPeriod.MONTH, Set.of(EvaluationType.IMPACT)).assets().getFirst();
        verify(this.evaluators.getFirst(), never()).computeCost(any(), any());
        assertEquals(1, impact.result().size());
        assertNull(impact.scaled().get(SimulationPeriod.DAY).cost());

        AssetResult cost = this.simulations.evaluate(SIMULATION, List.of(scratch("a")), SimulationPeriod.MONTH, Set.of(EvaluationType.COST)).assets().getFirst();
        verify(this.evaluators.get(1), never()).computeImpact(any(), any());
        assertTrue(cost.result().getFirst() instanceof AssetCost);
        assertNull(cost.scaled().get(SimulationPeriod.DAY).impact());
    }

    @Test
    void theWindowsFootprint_isScaledToEachPeriod_andSummedOverTheAssetsWithResults()
    {
        SimulationAssetEntity gone = scratch("gone");
        gone.type = "acme.gone";

        SimulationEvaluation evaluation = this.simulations.evaluate(SIMULATION, List.of(scratch("a"), copy("b"), gone), SimulationPeriod.MONTH, BOTH);

        AssetResult first = evaluation.assets().getFirst();
        assertEquals(30, gwp(first.scaled().get(SimulationPeriod.MONTH).impact()), 1e-3);
        assertEquals(1, gwp(first.scaled().get(SimulationPeriod.DAY).impact()), 1e-3);
        assertEquals(7, gwp(first.scaled().get(SimulationPeriod.WEEK).impact()), 1e-3);
        assertEquals(365, gwp(first.scaled().get(SimulationPeriod.YEAR).impact()), 1e-3);
        assertEquals(30, gwp(((AssetImpact) first.result().getFirst()).footprint()), 1e-3);

        assertEquals(2, gwp(evaluation.totals().get(SimulationPeriod.DAY).impact()), 1e-3);
        assertEquals(730, gwp(evaluation.totals().get(SimulationPeriod.YEAR).impact()), 1e-3);
        assertEquals(1.0 / 30, evaluation.factors().get(SimulationPeriod.DAY), 1e-9);
        assertEquals(365.0 / 30, evaluation.factors().get(SimulationPeriod.YEAR), 1e-9);
    }

    @Test
    void eachServicesFootprint_isScaled_andSummedOverTheEvaluations()
    {
        AssetIdentity identity = SimulationAssetService.identity(SIMULATION, "a", TYPE);
        List<AssetEvaluation> evaluations = List.of(
            new AssetImpact(identity, footprint(30), List.of(new Service("chat", footprint(20)), new Service("search", footprint(10))), ImpactProvider.ECOLOGITS, null),
            new AssetImpact(identity, footprint(30), List.of(new Service("chat", footprint(30))), ImpactProvider.ECOLOGITS, null)
        );

        List<ScaledService> services = SimulationEvaluator.scale(evaluations, SimulationPeriod.MONTH, BOTH).get(SimulationPeriod.YEAR).services();

        assertEquals(List.of("chat", "search"), services.stream().map(ScaledService::serviceId).toList());
        assertEquals(50 * 365.0 / 30, gwp(services.getFirst().impact()), 1e-1);
        assertEquals(LLMStaticModule.TYPE, services.getFirst().assetType());
        /* Costed only, impacts name no service */
        assertTrue(SimulationEvaluator.scale(evaluations, SimulationPeriod.MONTH, Set.of(EvaluationType.COST)).get(SimulationPeriod.YEAR).services().isEmpty());
        assertNull(SimulationEvaluator.scale(evaluations, SimulationPeriod.MONTH, Set.of()).get(SimulationPeriod.YEAR).services());
    }

    @Test
    void theWindowsCost_isScaledLikeItsFootprint_perServiceToo_andSummedPerCurrency()
    {
        SimulationEvaluation evaluation = this.simulations.evaluate(SIMULATION, List.of(scratch("a"), copy("b")), SimulationPeriod.MONTH, BOTH);

        AssetResult first = evaluation.assets().getFirst();
        assertEquals(List.of(Money.of(36.5, "EUR")), first.scaled().get(SimulationPeriod.YEAR).cost());
        ScaledService chat = first.scaled().get(SimulationPeriod.YEAR).services().getFirst();
        assertEquals("chat", chat.serviceId());
        assertEquals(List.of(Money.of(36.5, "EUR")), chat.cost());
        assertNull(chat.impact());
        assertEquals(List.of(Money.of(0.2, "EUR")), evaluation.totals().get(SimulationPeriod.DAY).cost());

        List<AssetEvaluation> mixed = List.of(
            new AssetCost(AssetIdentity.of("a", "e", TYPE), Money.of(30, "EUR"), List.of(), null),
            new AssetCost(AssetIdentity.of("a", "e", TYPE), Money.of(60, "USD"), List.of(), null)
        );
        assertEquals(List.of(Money.of(1, "EUR"), Money.of(2, "USD")), SimulationEvaluator.scale(mixed, SimulationPeriod.MONTH, BOTH).get(SimulationPeriod.DAY).cost());
    }

    @Test
    void aCostThatPricesNothing_isEvaluated_withoutAmount()
    {
        List<AssetEvaluation> unpriced = List.of(new AssetCost(AssetIdentity.of("a", "e", TYPE), null, List.of(), null));

        Scaled day = SimulationEvaluator.scale(unpriced, SimulationPeriod.MONTH, Set.of(EvaluationType.COST)).get(SimulationPeriod.DAY);

        assertTrue(day.cost().isEmpty());
        assertNull(day.impact());
    }

    @Test
    void coverage_isTheShareOfTheWindowsSlotsWithActivity()
    {
        this.coveredDays = 15;

        assertEquals(0.5, this.simulations.evaluate(SIMULATION, List.of(scratch("a")), SimulationPeriod.MONTH, BOTH).assets().getFirst().coverage(), 1e-9);
    }

    @Test
    void window_endsAtTheCurrentHourForADay_andAtTheCurrentDayOtherwise()
    {
        assertEquals(
            new SimulationEvaluation.Window(Instant.parse("2026-11-20T10:00:00Z"), Instant.parse("2026-11-21T10:00:00Z"), SimulationPeriod.DAY),
            this.simulations.window(SimulationPeriod.DAY)
        );
        assertEquals(
            new SimulationEvaluation.Window(Instant.parse("2026-10-22T00:00:00Z"), Instant.parse("2026-11-21T00:00:00Z"), SimulationPeriod.MONTH),
            this.simulations.window(SimulationPeriod.MONTH)
        );
    }

    @Test
    void aSimulationWithoutAssets_evaluatesToNothing()
    {
        SimulationEvaluation evaluation = this.simulations.evaluate(SIMULATION, List.of(), SimulationPeriod.WEEK, BOTH);

        assertTrue(evaluation.assets().isEmpty());
        assertEquals(0, gwp(evaluation.totals().get(SimulationPeriod.YEAR).impact()), 1e-9);
    }

    /* Resolves the whole window into one activity slot, and its daily split into as many as coveredDays */
    private AssetEvaluator newEvaluator()
    {
        AssetEvaluator evaluator = mock(AssetEvaluator.class);
        try {
            when(evaluator.computeActivity(any(), any(), isNull())).thenAnswer(invocation -> {
                if (this.failing instanceof Failure.Throws(Exception error))
                    throw error;
                if (this.failing instanceof Failure.None)
                    return Optional.empty();
                Asset asset = invocation.getArgument(0);
                TimeRange range = invocation.getArgument(1);
                List<ActivityData> slots = range.split().stream()
                    .limit(range.resolution().equals(Duration.between(range.start(), range.end())) ? 1 : this.coveredDays)
                    .map(slot -> (ActivityData) new LLMActivityData(ActivityRegime.MODELED, Set.of("m"), slot, Map.of()))
                    .toList();
                return Optional.of(new AssetActivity(asset.identity(), slots));
            });
            when(evaluator.computeImpact(any(), any())).thenAnswer(invocation -> {
                Asset asset = invocation.getArgument(0);
                ActivityData data = invocation.getArgument(1);
                return Optional.of(new AssetImpact(asset.identity(), footprint(30), List.of(), ImpactProvider.ECOLOGITS, data.range()));
            });
            when(evaluator.computeCost(any(), any())).thenAnswer(invocation -> {
                Asset asset = invocation.getArgument(0);
                ActivityData data = invocation.getArgument(1);
                return Optional.of(new AssetCost(asset.identity(), Money.of(3, "EUR"), List.of(new Service("chat", null, Money.of(3, "EUR"))), data.range()));
            });
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
        this.evaluators.add(evaluator);
        return evaluator;
    }

    private static double gwp(Footprint footprint)
    {
        return footprint.gwp().totalValue();
    }

    private static Footprint footprint(float gwp)
    {
        return new Footprint(impact("kgCO2eq", gwp), impact("MJ", gwp * 2), impact("kgSbeq", gwp / 10));
    }

    private static FootprintImpact impact(String unit, float use)
    {
        return new FootprintImpact(unit, "", new FootprintImpactItem(0f, List.of()), new FootprintImpactItem(use, List.of()));
    }

    /* Copied from the asset real-chatbot of the environment prod */
    private static SimulationAssetEntity copy(String id)
    {
        SimulationAssetEntity entity = scratch(id);
        entity.sourceAssetId = "real-chatbot";
        entity.sourceEnvironmentId = "prod";
        return entity;
    }

    private static SimulationAssetEntity scratch(String id)
    {
        SimulationAssetEntity entity = new SimulationAssetEntity();
        entity.simulationId = SIMULATION;
        entity.id = id;
        entity.name = id;
        entity.type = TYPE;
        entity.properties = new LLMStaticAsset(
            SimulationAssetService.identity(SIMULATION, id, TYPE),
            new LLMStaticProfile(LLMProvider.mistralai, "m", FRANCE, List.of(new ConstantScenario(500, 1000)))
        );
        return entity;
    }

    private static AssetDefinitionReader reader()
    {
        return new AssetDefinitionReader(
            new AssetValidator(ModuleRegistries.enabled(List.of(new LLMStaticModule()))),
            Validation.buildDefaultValidatorFactory().getValidator(),
            MAPPER
        );
    }

    /* A service of either kind: its footprint for an impact, its cost for a cost */
    private record Service(String serviceId, Footprint footprint, Money cost) implements ServiceImpact, ServiceCost
    {
        Service(String serviceId, Footprint footprint)
        {
            this(serviceId, footprint, null);
        }

        @Override
        public AssetProfile profile()
        {
            return null;
        }

        @Override
        public AssetType assetType()
        {
            return LLMStaticModule.TYPE;
        }
    }

    private sealed interface Failure
    {
        record None() implements Failure {}

        record Throws(Exception error) implements Failure {}
    }
}
