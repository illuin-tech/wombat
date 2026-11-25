package tech.illuin.wombat.engine.module.runtime;

import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.illuin.wombat.core.asset.type.FamilyType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.module.WombatModule;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.engine.module.library.kubernetes_simulated.KubernetesSimulatedModule;
import tech.illuin.wombat.engine.module.library.llm_simulated.LLMSimulatedModule;
import tech.illuin.wombat.engine.module.persistence.CoreDeploymentEntity;
import tech.illuin.wombat.engine.module.persistence.CoreDeploymentRepository;
import tech.illuin.wombat.engine.module.persistence.CoreInstanceEntity;
import tech.illuin.wombat.engine.module.persistence.CoreInstanceRepository;
import tech.illuin.wombat.engine.module.registry.InMemoryModuleRegistry;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata;
import tech.illuin.wombat.engine.module.registry.ModuleMetadata.Source;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreDeploymentServiceTest
{
    private static final String K8S = KubernetesSimulatedModule.TYPE.name();
    private static final String LLM = LLMSimulatedModule.TYPE.name();
    private static final FamilyModule ZETA = FamilyModule.of("zeta");
    private static final FamilyModule ALPHA = FamilyModule.of("alpha");

    private CoreDeploymentFactory factory;
    private CoreRuntime runtime;
    private InMemoryModuleRegistry registry;
    private CoreDeploymentRepository deployments;
    private CoreInstanceRepository instances;

    @BeforeEach
    void setUp()
    {
        this.factory = mock(CoreDeploymentFactory.class);
        /* Builds a deployment holding the requested modules, under the requested version */
        when(this.factory.create(anyLong(), anyString(), any())).thenAnswer(call -> deployment(call.getArgument(0), call.getArgument(2)));
        this.runtime = new CoreRuntime(this.factory, Runnable::run);
        this.registry = new InMemoryModuleRegistry();
        this.registry.registerModule(new KubernetesSimulatedModule(), true);
        this.registry.registerModule(new LLMSimulatedModule(), false);
        this.deployments = mock(CoreDeploymentRepository.class);
        this.instances = mock(CoreInstanceRepository.class);
    }

    @Test
    void startupDeploysTheLatestDeployment_notTheRegistry()
    {
        when(this.deployments.findLatest()).thenReturn(Optional.of(recorded(4, K8S, LLM)));

        this.service(true).onStart(null);

        assertEquals(4, this.runtime.current().version());
        assertEquals(Set.of(K8S, LLM), CoreRuntime.names(this.runtime.current().types()), "the registry disables LLM, but no redeploy applied it");
    }

    @Test
    void theFirstStartupRecordsTheModulesTheRegistryEnables()
    {
        when(this.deployments.findLatest()).thenReturn(Optional.empty());
        when(this.deployments.insertNext(List.of(K8S), null)).thenReturn(recorded(1, K8S));

        this.service(true).onStart(null);

        assertEquals(1, this.runtime.current().version());
        verify(this.deployments).insertNext(List.of(K8S), null);
    }

    @Test
    void aStartupDeploysTheModulesRegisteredForTheFirstTime()
    {
        this.registry = newlyRegistering(Source.extension, LLM);
        when(this.deployments.findLatest()).thenReturn(Optional.of(recorded(4, K8S)));
        when(this.deployments.insertNext(List.of(K8S, LLM), null)).thenReturn(recorded(5, K8S, LLM));

        this.service(true).onStart(null);

        assertEquals(5, this.runtime.current().version());
        assertEquals(Set.of(K8S, LLM), CoreRuntime.names(this.runtime.current().types()));
    }

    @Test
    void aStartupRecordsNothingWhenTheNewModulesAreDeployedAlready()
    {
        this.registry = newlyRegistering(Source.core, LLM);
        when(this.deployments.findLatest()).thenReturn(Optional.of(recorded(4, K8S, LLM)));

        this.service(true).onStart(null);

        assertEquals(4, this.runtime.current().version());
        verify(this.deployments, never()).insertNext(any(), any());
    }

    @Test
    void aStartupAddingNewModulesWhileAnotherDoesTakesTheDeploymentItRecorded()
    {
        this.registry = newlyRegistering(Source.core, LLM);
        when(this.deployments.findLatest()).thenReturn(Optional.of(recorded(4, K8S)), Optional.of(recorded(5, K8S, LLM)));
        when(this.deployments.insertNext(any(), isNull())).thenThrow(new PersistenceException("duplicate version"));

        this.service(true).onStart(null);

        assertEquals(5, this.runtime.current().version());
    }

    @Test
    void aStartupLeavesTheNewExtensionModulesToARedeploy_whenAutoDeployIsOff()
    {
        this.registry = newlyRegistering(Source.extension, LLM);
        when(this.deployments.findLatest()).thenReturn(Optional.of(recorded(4, K8S)));

        this.service(true, false).onStart(null);

        assertEquals(4, this.runtime.current().version());
        verify(this.deployments, never()).insertNext(any(), any());
    }

    @Test
    void aStartupDeploysTheNewModulesTheAppShips_evenWhenAutoDeployIsOff()
    {
        this.registry = newlyRegistering(Source.core, LLM);
        when(this.deployments.findLatest()).thenReturn(Optional.of(recorded(4, K8S)));
        when(this.deployments.insertNext(List.of(K8S, LLM), null)).thenReturn(recorded(5, K8S, LLM));

        this.service(true, false).onStart(null);

        assertEquals(5, this.runtime.current().version());
    }

    @Test
    void aStartupRacingAnotherTakesTheDeploymentItRecorded()
    {
        when(this.deployments.findLatest()).thenReturn(Optional.empty(), Optional.of(recorded(1, K8S, LLM)));
        when(this.deployments.insertNext(any(), isNull())).thenThrow(new PersistenceException("duplicate version"));

        this.service(true).onStart(null);

        assertEquals(Set.of(K8S, LLM), CoreRuntime.names(this.runtime.current().types()));
    }

    @Test
    void aRedeployRecordsTheEnabledModulesAndDeploysThemHere()
    {
        CoreDeploymentService service = this.started(recorded(1, K8S, LLM));
        when(this.deployments.insertNext(List.of(K8S), "alice")).thenReturn(recorded(2, K8S));

        CoreDeploymentService.Redeploy redeploy = service.redeploy("alice");

        assertEquals(2, this.runtime.current().version());
        assertEquals(CoreInstanceEntity.InstanceStatus.ok, redeploy.local().status());
        assertEquals(Set.of(), redeploy.local().added());
        assertEquals(Set.of(LLM), redeploy.local().removed());
    }

    @Test
    void aRedeployRecordsTheAssetTypeModulesByFamily_thenTheFamilyModulesInTheRegistryOrder()
    {
        this.registry = new InMemoryModuleRegistry();
        this.registry.registerModule(new LLMSimulatedModule(), true);
        this.registry.registerModule(ZETA, true);
        this.registry.registerModule(new KubernetesSimulatedModule(), true);
        this.registry.registerModule(ALPHA, true);
        CoreDeploymentService service = this.started(recorded(1, K8S));
        when(this.deployments.insertNext(any(), any())).thenReturn(recorded(2, K8S));

        service.redeploy("alice");

        verify(this.deployments).insertNext(List.of(K8S, LLM, ZETA.type().name(), ALPHA.type().name()), "alice");
    }

    @Test
    void aStartupAppendsTheNewModulesToTheLatestDeployment_leavingTheRegistryOrderToARedeploy()
    {
        FamilyModule gamma = FamilyModule.of("gamma");
        this.registry = newlyRegistering(Source.core, List.of(new KubernetesSimulatedModule(), ZETA, ALPHA, gamma), gamma.type().name());
        when(this.deployments.findLatest()).thenReturn(Optional.of(recorded(4, K8S, ALPHA.type().name(), ZETA.type().name())));
        when(this.deployments.insertNext(any(), isNull())).thenReturn(recorded(5, K8S));

        this.service(true).onStart(null);

        verify(this.deployments).insertNext(List.of(K8S, ALPHA.type().name(), ZETA.type().name(), gamma.type().name()), null);
    }

    @Test
    void aRedeployRecordedAtTheSameTimeAsAnotherIsAConflict()
    {
        CoreDeploymentService service = this.started(recorded(1, K8S));
        when(this.deployments.insertNext(any(), any())).thenThrow(new PersistenceException("duplicate version"));

        assertThrows(DeploymentConflictException.class, () -> service.redeploy("alice"));
    }

    @Test
    void aFailedBuildIsReported_keepsTheRunningCore_andShowsInTheHeartbeat()
    {
        CoreDeploymentService service = this.started(recorded(1, K8S));
        when(this.deployments.insertNext(any(), any())).thenReturn(recorded(2, K8S));
        doThrow(new IllegalStateException("broken module")).when(this.factory).create(eq(2L), anyString(), any());

        CoreDeploymentService.Redeploy redeploy = service.redeploy("alice");

        assertEquals(CoreInstanceEntity.InstanceStatus.failed, redeploy.local().status());
        assertEquals("broken module", redeploy.local().error());
        assertEquals(1, this.runtime.current().version());
        CoreInstanceEntity heartbeat = this.lastHeartbeat();
        assertEquals(CoreInstanceEntity.InstanceStatus.failed, heartbeat.status);
        assertEquals(1, heartbeat.deployedVersion);
    }

    @Test
    void aPollDeploysTheLatestDeploymentWhenBehind()
    {
        CoreDeploymentService service = this.started(recorded(1, K8S));
        when(this.deployments.findLatest()).thenReturn(Optional.of(recorded(3, K8S, LLM)));

        service.sync();

        assertEquals(3, this.runtime.current().version());
        assertEquals(3, this.lastHeartbeat().deployedVersion);
    }

    @Test
    void aPollDoesNotRetryAFailedVersion_butANewerOne()
    {
        CoreDeploymentService service = this.started(recorded(1, K8S));
        doThrow(new IllegalStateException("broken module")).when(this.factory).create(eq(2L), anyString(), any());
        when(this.deployments.findLatest()).thenReturn(Optional.of(recorded(2, K8S)));

        service.sync();
        service.sync();
        verify(this.factory, times(1)).create(eq(2L), anyString(), any());

        when(this.deployments.findLatest()).thenReturn(Optional.of(recorded(3, K8S)));
        service.sync();
        assertEquals(3, this.runtime.current().version());
        assertEquals(CoreInstanceEntity.InstanceStatus.ok, this.lastHeartbeat().status);
        assertNull(this.lastHeartbeat().error);
    }

    @Test
    void theHeartbeatTellsTheDeployedVersion_theDetectedModules_andTheMissingOnes()
    {
        this.started(recorded(1, K8S, "acme.removed"));

        CoreInstanceEntity heartbeat = this.lastHeartbeat();

        assertEquals("instance-a", heartbeat.instanceId);
        assertEquals(1, heartbeat.deployedVersion);
        assertEquals(Set.of(K8S, LLM), heartbeat.detectedModules);
        assertEquals(Set.of("acme.removed"), heartbeat.missingModules);
    }

    @Test
    void aWorkerDeploys_butNeitherPollsNorWritesAHeartbeat()
    {
        when(this.deployments.findLatest()).thenReturn(Optional.of(recorded(1, K8S)));
        CoreDeploymentService service = this.service(false);

        service.onStart(null);
        service.sync();

        assertEquals(1, this.runtime.current().version());
        verify(this.instances, never()).upsert(any());
        verify(this.deployments, times(1)).findLatest();
    }

    private CoreDeploymentService started(CoreDeploymentEntity latest)
    {
        when(this.deployments.findLatest()).thenReturn(Optional.of(latest));
        CoreDeploymentService service = this.service(true);
        service.onStart(null);
        return service;
    }

    private CoreDeploymentService service(boolean heartbeat)
    {
        return this.service(heartbeat, true);
    }

    private CoreDeploymentService service(boolean heartbeat, boolean autoDeployExtensions)
    {
        List<WombatModule> modules = List.of(new KubernetesSimulatedModule(), new LLMSimulatedModule());
        return new CoreDeploymentService(this.runtime, this.registry, this.deployments, this.instances, modules, "instance-a", Duration.ofSeconds(30), heartbeat, autoDeployExtensions);
    }

    private static InMemoryModuleRegistry newlyRegistering(Source source, String... names)
    {
        return newlyRegistering(source, List.of(new KubernetesSimulatedModule(), new LLMSimulatedModule()), names);
    }

    /**
     * Stands for the registry of an instance which registered {@code names} for the first time at its startup, among
     * {@code modules} in that order, its modules all coming from {@code source}.
     */
    private static InMemoryModuleRegistry newlyRegistering(Source source, List<WombatModule> modules, String... names)
    {
        InMemoryModuleRegistry registry = new InMemoryModuleRegistry()
        {
            @Override
            public Set<String> newlyRegistered()
            {
                return Set.of(names);
            }

            @Override
            public List<ModuleMetadata> listModules()
            {
                return super.listModules().stream()
                    .map(found -> new ModuleMetadata(found.type(), found.assetClass(), found.enabled(), found.missing(), source))
                    .toList();
            }
        };
        modules.forEach(module -> registry.registerModule(module, true));
        return registry;
    }

    private CoreInstanceEntity lastHeartbeat()
    {
        ArgumentCaptor<CoreInstanceEntity> heartbeats = ArgumentCaptor.forClass(CoreInstanceEntity.class);
        verify(this.instances, atLeastOnce()).upsert(heartbeats.capture());
        return heartbeats.getValue();
    }

    private static CoreDeploymentEntity recorded(long version, String... modules)
    {
        return CoreDeploymentEntity.of(version, Instant.now(), null, List.of(modules));
    }

    /* Holds the requested modules among those of this test; the others are reported missing */
    private static CoreDeployment deployment(long version, List<String> requested)
    {
        List<WombatModule> known = List.of(new KubernetesSimulatedModule(), new LLMSimulatedModule());
        List<WombatModule> deployed = known.stream().filter(module -> requested.contains(module.type().name())).toList();
        Set<String> missing = requested.stream().filter(name -> deployed.stream().noneMatch(module -> module.type().name().equals(name))).collect(Collectors.toSet());
        AssetEvaluator evaluator = mock(AssetEvaluator.class);
        return new CoreDeployment(version, "id-" + version, deployed, missing, mock(AssetMonitor.class), evaluator, () -> evaluator, Instant.now());
    }

    /* Serves a family with nothing: only its name, and its place among the modules, matter here */
    private record FamilyModule(FamilyType type) implements WombatModule
    {
        static FamilyModule of(String localName)
        {
            return new FamilyModule(FamilyType.of("acme", "test", localName, ServiceFamily.LLM));
        }
    }
}
