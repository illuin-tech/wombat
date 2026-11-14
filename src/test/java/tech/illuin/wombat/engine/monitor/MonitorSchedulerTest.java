package tech.illuin.wombat.engine.monitor;

import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;
import tech.illuin.wombat.commons.NodeMode;
import tech.illuin.wombat.commons.WombatProperties;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonitorSchedulerTest
{
    @Test
    void standaloneAndWorkers_sampleTheirEnvironments()
    {
        for (NodeMode mode : new NodeMode[]{ NodeMode.standalone, NodeMode.worker })
        {
            Scheduler scheduler = startedScheduler();
            Instance<MonitorService> service = service();
            new MonitorScheduler(scheduler, service, wombat(mode)).registerMonitorService(null);
            verify(scheduler).newJob("wombat-monitor");
            verify(service).get();
        }
    }

    /* Not even creating the monitor, whose sources would set up clients for clusters only the workers reach. */
    @Test
    void aggregator_leavesTheSamplingToItsWorkers()
    {
        Scheduler scheduler = startedScheduler();
        Instance<MonitorService> service = service();
        new MonitorScheduler(scheduler, service, wombat(NodeMode.aggregator)).registerMonitorService(null);
        verify(scheduler, never()).newJob(anyString());
        verify(service, never()).get();
    }

    @SuppressWarnings("unchecked")
    private static Instance<MonitorService> service()
    {
        Instance<MonitorService> service = mock(Instance.class);
        when(service.get()).thenReturn(mock(MonitorService.class));
        return service;
    }

    private static Scheduler startedScheduler()
    {
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.isStarted()).thenReturn(true);
        when(scheduler.newJob(anyString())).thenReturn(mock(Scheduler.JobDefinition.class, RETURNS_SELF));
        return scheduler;
    }

    private static WombatProperties wombat(NodeMode mode)
    {
        WombatProperties wombat = mock(WombatProperties.class, RETURNS_DEEP_STUBS);
        when(wombat.mode()).thenReturn(mode);
        return wombat;
    }
}
