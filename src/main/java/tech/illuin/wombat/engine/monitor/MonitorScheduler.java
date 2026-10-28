package tech.illuin.wombat.engine.monitor;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class MonitorScheduler
{
    private final Scheduler scheduler;
    private final MonitorService service;
    private final MonitorProperties properties;

    private static final Logger logger = LoggerFactory.getLogger(MonitorScheduler.class);

    @Inject
    public MonitorScheduler(Scheduler scheduler, MonitorService service, MonitorProperties properties)
    {
        this.scheduler = scheduler;
        this.service = service;
        this.properties = properties;
    }

    public void registerMonitorService(@Observes StartupEvent event)
    {
        if (!scheduler.isStarted())
        {
            logger.warn("Scheduler is not started, skipping monitor service scheduling");
            return;
        }

        String interval = this.properties.heartbeat().asText();

        this.scheduler.newJob("wombat-monitor")
            .setInterval(interval)
            .setTask(ctx -> this.service.monitor())
            .schedule();
    }
}
