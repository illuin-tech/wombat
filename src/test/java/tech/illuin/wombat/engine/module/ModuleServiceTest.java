package tech.illuin.wombat.engine.module;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.engine.context.asset.persistence.AssetRepository;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.engine.module.persistence.CoreDeploymentEntity;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleRegistry;
import tech.illuin.wombat.engine.module.registry.UnknownModuleException;
import tech.illuin.wombat.engine.module.runtime.CoreDeployment;
import tech.illuin.wombat.engine.module.runtime.CoreDeploymentService;
import tech.illuin.wombat.engine.module.runtime.CoreRuntime;

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
    void list_putsTheDetectedModulesFirst_thenTheMissingOnes()
    {
        this.registered(missing(REMOVED), detected(LLM_MODULE, true), detected(K8S_MODULE, true));
        this.deployed();

        List<String> names = this.service.list().stream().map(module -> module.type().name()).toList();

        assertEquals(List.of(K8S, LLM, REMOVED.name()), names);
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

    private void latest(String... enabled)
    {
        when(this.deployments.latest()).thenReturn(Optional.of(CoreDeploymentEntity.of(1, Instant.now(), null, Set.of(enabled))));
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
        return ModuleMetadata.core(module.type(), module.assetClass(), enabled);
    }

    private static ModuleMetadata missing(AssetType type)
    {
        return new ModuleMetadata(type, null, true, true, null);
    }

    private static Map<String, ModuleInfo> byName(List<ModuleInfo> modules)
    {
        return modules.stream().collect(Collectors.toMap(module -> module.type().name(), module -> module));
    }
}
