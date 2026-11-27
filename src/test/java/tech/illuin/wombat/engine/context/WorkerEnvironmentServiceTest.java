package tech.illuin.wombat.engine.context;

import org.junit.jupiter.api.Test;
import tech.illuin.wombat.commons.NodeMode;
import tech.illuin.wombat.commons.WombatProperties;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.evaluation.AssetEvaluator;
import tech.illuin.wombat.core.source.AssetMonitor;
import tech.illuin.wombat.engine.context.asset.AssetValidator;
import tech.illuin.wombat.engine.monitor.MonitoredEnvironments;
import tech.illuin.wombat.engine.monitor.RequiredSecretsCheck;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkerEnvironmentServiceTest
{
    private static final MonitoredEnvironments NONE = new MonitoredEnvironments(Map.of());
    private static final MonitoredEnvironments FILE = new MonitoredEnvironments(Map.of("file", new Environment("file", List.of())));

    private final EnvironmentReconciler reconciler = mock(EnvironmentReconciler.class);
    private final RequiredSecretsCheck secrets = mock(RequiredSecretsCheck.class);
    private final AssetMonitor monitor = mock(AssetMonitor.class);
    private final AssetEvaluator evaluator = mock(AssetEvaluator.class);

    @Test
    void workerWithoutApi_declaresItsFile()
    {
        WorkerEnvironmentService service = this.service(NodeMode.worker, false, FILE);

        assertEquals(Optional.of(FILE), service.current());
        assertThrows(IllegalStateException.class, () -> service.declare(NONE));
    }

    /* After a restart, declaring only the file would delete the API's assets on the aggregator. */
    @Test
    void workerWithApi_declaresNothingUntilItsFirstDeclaration_thenTheFileAndTheDeclaration()
    {
        when(this.secrets.missingSecrets(any())).thenReturn(Set.of());
        when(this.reconciler.reconcile(any(), anyMap(), any())).thenReturn(new EnvironmentReconciler.Result(0, 0, 0));
        WorkerEnvironmentService service = this.service(NodeMode.worker, true, FILE);

        assertEquals(Optional.empty(), service.current());
        assertEquals(Optional.empty(), service.declared());

        MonitoredEnvironments declared = new MonitoredEnvironments(Map.of("ns", new Environment("ns", List.of())));
        service.declare(declared);

        assertEquals(Optional.of(declared), service.declared());
        assertEquals(Set.of("file", "ns"), service.current().orElseThrow().environments().keySet());
        verify(this.reconciler).reconcile(eq(EnvironmentReconciler.WORKER_API_OWNER), eq(declared.environments()), any());
    }

    @Test
    void merge_concatenatesTheAssetsOfAnEnvironmentDeclaredByBoth()
    {
        Asset fromFile = mock(Asset.class);
        Asset fromApi = mock(Asset.class);
        MonitoredEnvironments file = new MonitoredEnvironments(Map.of(
            "shared", new Environment("shared", List.of(fromFile)),
            "file-only", new Environment("file-only", List.of())
        ));
        MonitoredEnvironments declared = new MonitoredEnvironments(Map.of("shared", new Environment("shared", List.of(fromApi))));

        MonitoredEnvironments merged = WorkerEnvironmentService.merge(file, declared);

        assertEquals(Set.of("shared", "file-only"), merged.environments().keySet());
        assertEquals(List.of(fromFile, fromApi), merged.environments().get("shared").assets());
    }

    /* The API only applies to workers. */
    @Test
    void standalone_ignoresTheApi()
    {
        WorkerEnvironmentService service = this.service(NodeMode.standalone, true, FILE);

        assertEquals(Optional.of(FILE), service.current());
        assertThrows(IllegalStateException.class, () -> service.declare(NONE));
    }

    @Test
    void declaration_refreshesTheChangedAssets()
    {
        AssetIdentity changed = AssetIdentity.of("ns-cluster", "ns", "tech.illuin.wombat-module.kubernetes-api");
        when(this.secrets.missingSecrets(any())).thenReturn(Set.of());
        doAnswer(invocation -> {
            Consumer<AssetIdentity> callback = invocation.getArgument(2);
            callback.accept(changed);
            return new EnvironmentReconciler.Result(0, 1, 0);
        }).when(this.reconciler).reconcile(any(), anyMap(), any());

        this.service(NodeMode.worker, true, NONE).declare(NONE);

        verify(this.monitor).refresh(changed);
        verify(this.evaluator).refresh(changed);
    }

    private WorkerEnvironmentService service(NodeMode mode, boolean api, MonitoredEnvironments file)
    {
        WombatProperties wombat = mock(WombatProperties.class, RETURNS_DEEP_STUBS);
        when(wombat.mode()).thenReturn(mode);
        when(wombat.worker().environments().api()).thenReturn(api);
        return new WorkerEnvironmentService(wombat, file, this.reconciler, mock(AssetValidator.class), this.secrets, this.monitor, this.evaluator);
    }
}
