package tech.illuin.wombat.engine.module;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.FamilyType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.engine.module.ModulePrecedence.FamilyPrecedence;
import tech.illuin.wombat.engine.module.ModulePrecedence.FeaturePrecedence;
import tech.illuin.wombat.engine.module.persistence.CoreDeploymentEntity;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata.Feature;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tech.illuin.wombat.engine.module.registry.UnknownModuleException;
import tech.illuin.wombat.engine.module.runtime.CoreDeployment;
import tech.illuin.wombat.engine.module.runtime.CoreDeploymentService;
import tech.illuin.wombat.engine.module.runtime.CoreRuntime;
import tech.illuin.wombat.module.boavizta.BoaviztaModule;
import tech.illuin.wombat.module.kubernetes_metrics.KubernetesMetricsModule;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModuleServiceTest
{
    private static final KubernetesSimulatedModule K8S_MODULE = new KubernetesSimulatedModule();
    private static final LLMSimulatedModule LLM_MODULE = new LLMSimulatedModule();
    private static final String K8S = K8S_MODULE.type().name();
    private static final String LLM = LLM_MODULE.type().name();
    private static final AssetType REMOVED = AssetType.of("acme", "removed", ActivityRegime.MEASURED, ServiceFamily.LLM);
    private static final AssetType GONE = AssetType.of("acme", "gone", ActivityRegime.MEASURED, ServiceFamily.LLM);
    /* An extension estimating the impacts of Kubernetes containers too, competing with Boavizta */
    private static final FamilyType ESTIMATOR = FamilyType.of("acme", "estimator", ServiceFamily.KUBERNETES_CONTAINER);
    private static final KubernetesMetricsModule METRICS_MODULE = new KubernetesMetricsModule();
    private static final BoaviztaModule BOAVIZTA_MODULE = new BoaviztaModule();
    private static final String METRICS = METRICS_MODULE.type().name();
    private static final String BOAVIZTA = BOAVIZTA_MODULE.type().name();

    private ModuleRegistry registry;
    private AssetRepository assets;
    private CoreRuntime runtime;
    private CoreDeploymentService deployments;
    private ModuleService service;

    @BeforeEach
    void setUp()
    {
        this.registry = mock(ModuleRegistry.class);
        this.assets = mock(AssetRepository.class);
        this.runtime = mock(CoreRuntime.class);
        this.deployments = mock(CoreDeploymentService.class);
        this.service = new ModuleService(this.registry, this.assets, this.runtime, this.deployments);
        this.latest(K8S, LLM);

        when(this.assets.countVisibleByType()).thenReturn(Map.of(K8S, 3L));
        when(this.registry.getModule(any(String.class))).thenReturn(Optional.empty());
    }

    @Test
    void aModuleDisabledSinceTheLatestDeploymentIsPending()
    {
        this.registered(detected(K8S_MODULE, true), detected(LLM_MODULE, false));
        this.deployed(K8S_MODULE, LLM_MODULE);

        Map<String, ModuleInfo> modules = byName(this.service.list());

        assertTrue(modules.get(K8S).deployed());
        assertFalse(modules.get(K8S).pending());
        assertEquals(3, modules.get(K8S).assetCount());

        assertFalse(modules.get(LLM).enabled());
        assertTrue(modules.get(LLM).deployed());
        assertTrue(modules.get(LLM).pending(), "disabled in the registry, still in the latest deployment until a redeploy");
        assertEquals(0, modules.get(LLM).assetCount());
    }

    @Test
    void aMissingModuleIsNeverPending_sinceARedeployCannotBringItIn()
    {
        this.registered(missing(REMOVED));
        this.deployed();

        ModuleInfo module = this.service.list().getFirst();

        assertTrue(module.missing());
        assertFalse(module.deployed());
        assertFalse(module.pending());
    }

    @Test
    void pendingComparesWithTheLatestDeployment_notWithTheLocalCore()
    {
        this.registered(detected(K8S_MODULE, true));
        this.latest(K8S);
        this.deployed();

        ModuleInfo module = this.service.list().getFirst();

        assertFalse(module.deployed(), "this instance lags behind");
        assertFalse(module.pending(), "the latest deployment holds it: no redeploy is needed");
    }

    @Test
    void list_followsTheOrderOfTheModules_missingOnesIncluded()
    {
        this.registered(ordered(K8S_MODULE, true, 3), missing(REMOVED, 1), ordered(LLM_MODULE, true, 2), detected(BOAVIZTA_MODULE, true));
        this.deployed();

        List<String> names = this.service.list().stream().map(module -> module.type().name()).toList();

        assertEquals(List.of(REMOVED.name(), LLM, K8S, BOAVIZTA), names, "a module not placed yet comes last");
    }

    @Test
    void setEnabled_goesThroughTheRegistry()
    {
        when(this.registry.getModule(K8S)).thenReturn(Optional.of(detected(K8S_MODULE, false)));
        this.deployed(K8S_MODULE);

        ModuleInfo module = this.service.setEnabled(K8S, false, "alice");

        verify(this.registry).setEnabled(K8S, false, "alice");
        assertTrue(module.pending());
    }

    @Test
    void find_onAnUnknownModuleIsNotFound()
    {
        assertThrows(UnknownModuleException.class, () -> this.service.find("acme.unknown"));
    }

    @Test
    void deployment_listsThePendingModules()
    {
        this.registered(detected(K8S_MODULE, false), detected(LLM_MODULE, true), missing(REMOVED));
        this.latest(K8S);
        when(this.deployments.liveInstances()).thenReturn(List.of());
        when(this.deployments.pollInterval()).thenReturn(Duration.ofSeconds(30));

        DeploymentInfo deployment = this.service.deployment();

        assertEquals(List.of(new DeploymentInfo.PendingModule(K8S, false), new DeploymentInfo.PendingModule(LLM, true)), deployment.pending());
        assertEquals(1, deployment.latest().version());
        assertEquals(30, deployment.pollIntervalSeconds());
    }

    @Test
    void list_tellsTheOrderingOfEachModule_andWhatItProvides()
    {
        this.registered(ordered(K8S_MODULE, true, 1), ordered(BOAVIZTA_MODULE, true, 2));
        this.latest(K8S, BOAVIZTA);
        this.deployed(K8S_MODULE, BOAVIZTA_MODULE);

        Map<String, ModuleInfo> modules = byName(this.service.list());

        assertEquals(2, modules.get(BOAVIZTA).ordering());
        assertEquals(Set.of(Feature.impact), modules.get(BOAVIZTA).provides());
    }

    @Test
    void aModuleMovedToTheTopIsTheOnlyOneMoved_apartFromTheToggles()
    {
        this.registered(ordered(BOAVIZTA_MODULE, true, 1), ordered(K8S_MODULE, true, 2), ordered(METRICS_MODULE, true, 3));
        this.latest(K8S, METRICS, BOAVIZTA);
        this.deployed(K8S_MODULE, METRICS_MODULE, BOAVIZTA_MODULE);
        when(this.deployments.liveInstances()).thenReturn(List.of());
        when(this.deployments.pollInterval()).thenReturn(Duration.ofSeconds(30));

        DeploymentInfo deployment = this.service.deployment();
        Map<String, ModuleInfo> modules = byName(this.service.list());

        assertEquals(List.of(BOAVIZTA), deployment.moved());
        assertEquals(List.of(), deployment.pending());
        assertTrue(modules.get(BOAVIZTA).pending());
        assertFalse(modules.get(K8S).pending());
        assertFalse(modules.get(METRICS).pending());
    }

    @Test
    void twoModulesSwapped_countAsOneMoved()
    {
        this.registered(ordered(METRICS_MODULE, true, 1), ordered(K8S_MODULE, true, 2));
        this.latest(K8S, METRICS);
        when(this.deployments.liveInstances()).thenReturn(List.of());
        when(this.deployments.pollInterval()).thenReturn(Duration.ofSeconds(30));

        assertEquals(1, this.service.deployment().moved().size());
    }

    @Test
    void nothingMoved_whenTheLatestDeploymentHoldsTheModulesItDeploysInTheRegistryOrder()
    {
        /* Boavizta is disabled since, the removed module was never deployed, and a missing one moved: none of them counts */
        this.registered(ordered(K8S_MODULE, true, 2), ordered(BOAVIZTA_MODULE, false, 3), missing(REMOVED, 4), ordered(METRICS_MODULE, true, 5),
            new ModuleMetadata(GONE, null, true, true, null, 1, Set.of()));
        this.latest(K8S, BOAVIZTA, GONE.name(), METRICS);
        when(this.deployments.liveInstances()).thenReturn(List.of());
        when(this.deployments.pollInterval()).thenReturn(Duration.ofSeconds(30));

        DeploymentInfo deployment = this.service.deployment();

        assertEquals(List.of(), deployment.moved());
        assertEquals(List.of(new DeploymentInfo.PendingModule(BOAVIZTA, false)), deployment.pending());
    }

    @Test
    void setOrder_goesThroughTheRegistry_andListsTheModulesInTheirNewOrder()
    {
        this.registered(ordered(K8S_MODULE, true, 2), ordered(BOAVIZTA_MODULE, true, 1));
        this.latest(K8S, BOAVIZTA);
        this.deployed(K8S_MODULE, BOAVIZTA_MODULE);

        List<ModuleInfo> modules = this.service.setOrder(List.of(BOAVIZTA, K8S), "alice");

        verify(this.registry).setOrder(List.of(BOAVIZTA, K8S), "alice");
        assertEquals(List.of(BOAVIZTA, K8S), modules.stream().map(module -> module.type().name()).toList());
    }

    @Test
    void precedence_listsTheFamilyModulesProvidingEachComponent_inTheirOrder_withoutTheAssetTypeModules()
    {
        this.registered(ordered(K8S_MODULE, true, 1), ordered(METRICS_MODULE, true, 2), family(ESTIMATOR, true, 3, Feature.impact), ordered(BOAVIZTA_MODULE, true, 4));
        this.latest(K8S, METRICS, ESTIMATOR.name(), BOAVIZTA);

        ModulePrecedence precedence = this.service.precedence();

        assertEquals(new ModulePrecedence(List.of(new FamilyPrecedence(ServiceFamily.KUBERNETES_CONTAINER, List.of(
            new FeaturePrecedence(Feature.activity, List.of(METRICS), List.of(METRICS), false),
            new FeaturePrecedence(Feature.impact, List.of(ESTIMATOR.name(), BOAVIZTA), List.of(ESTIMATOR.name(), BOAVIZTA), false)
        ))), false), precedence);
    }

    @Test
    void precedence_tellsHowTheNextDeploymentChangesIt()
    {
        /* Boavizta moved before the estimator, and the metrics disabled, since the latest deployment */
        this.registered(ordered(BOAVIZTA_MODULE, true, 1), family(ESTIMATOR, true, 2, Feature.impact), ordered(METRICS_MODULE, false, 3));
        this.latest(METRICS, ESTIMATOR.name(), BOAVIZTA);

        ModulePrecedence precedence = this.service.precedence();

        assertTrue(precedence.changed());
        assertEquals(List.of(
            new FeaturePrecedence(Feature.activity, List.of(METRICS), List.of(), true),
            new FeaturePrecedence(Feature.impact, List.of(ESTIMATOR.name(), BOAVIZTA), List.of(BOAVIZTA, ESTIMATOR.name()), true)
        ), precedence.families().getFirst().features());
    }

    @Test
    void precedence_leavesOutTheModulesThisInstanceDoesNotDetect_whoseComponentsItCannotTell()
    {
        this.registered(ordered(BOAVIZTA_MODULE, true, 1), new ModuleMetadata(ESTIMATOR, null, true, true, null, 2, Set.of()));
        this.latest(BOAVIZTA, ESTIMATOR.name());

        assertEquals(List.of(BOAVIZTA), this.service.precedence().families().getFirst().features().getFirst().deployed());
    }

    private void latest(String... enabled)
    {
        when(this.deployments.latest()).thenReturn(Optional.of(CoreDeploymentEntity.of(1, Instant.now(), null, List.of(enabled))));
    }

    private void registered(ModuleMetadata... modules)
    {
        when(this.registry.listModules()).thenReturn(List.of(modules));
    }

    private void deployed(WombatModule... modules)
    {
        AssetEvaluator evaluator = mock(AssetEvaluator.class);
        when(this.runtime.current()).thenReturn(new CoreDeployment(1, "id", List.of(modules), Set.of(), mock(AssetMonitor.class), evaluator, () -> evaluator, Instant.now()));
    }

    private static ModuleMetadata detected(WombatModule module, boolean enabled)
    {
        return ModuleMetadata.core(module, enabled, null);
    }

    private static ModuleMetadata ordered(WombatModule module, boolean enabled, int ordering)
    {
        return ModuleMetadata.core(module, enabled, ordering);
    }

    /* A family module from the extension folder, providing the given components */
    private static ModuleMetadata family(FamilyType type, boolean enabled, int ordering, Feature... features)
    {
        return new ModuleMetadata(type, null, enabled, false, ModuleMetadata.Source.extension, ordering, Set.of(features));
    }

    private static ModuleMetadata missing(AssetType type)
    {
        return missing(type, null);
    }

    private static ModuleMetadata missing(AssetType type, Integer ordering)
    {
        return new ModuleMetadata(type, null, true, true, null, ordering, Set.of());
    }

    private static Map<String, ModuleInfo> byName(List<ModuleInfo> modules)
    {
        return modules.stream().collect(Collectors.toMap(module -> module.type().name(), module -> module));
    }
}
