package tech.illuin.wombat.engine.monitor;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.commons.NodeMode;
import tech.illuin.wombat.commons.WombatProperties;

@ApplicationScoped
public class MonitorScheduler
{
    private final Scheduler scheduler;
    private final Instance<MonitorService> service;
    private final WombatProperties wombat;

    private static final Logger logger = LoggerFactory.getLogger(MonitorScheduler.class);

    @Inject
    public MonitorScheduler(
        Scheduler scheduler,
        Instance<MonitorService> service,
        WombatProperties wombat
    ) {
        this.scheduler = scheduler;
        this.service = service;
        this.wombat = wombat;
    }

    public void registerMonitorService(@Observes StartupEvent event)
    {
        if (this.wombat.mode() == NodeMode.aggregator)
        {
            logger.info("Running in aggregator mode, environments are sampled by workers: skipping monitor service scheduling");
            return;
        }

        if (!scheduler.isStarted())
        {
            logger.warn("Scheduler is not started, skipping monitor service scheduling");
            return;
        }

        MonitorService monitor = this.service.get();
        String interval = this.wombat.monitor().heartbeat().asText();

        this.scheduler.newJob("wombat-monitor")
            .setInterval(interval)
            .setTask(_ -> monitor.monitor())
            .schedule();
    }
}
