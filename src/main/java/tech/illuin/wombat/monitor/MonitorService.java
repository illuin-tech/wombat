package tech.illuin.wombat.monitor;

import io.quarkus.scheduler.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.illuin.wombat.core.source.AssetMonitor;

public class MonitorService
{
    private final AssetMonitor assetMonitor;

    private static final Logger logger = LoggerFactory.getLogger(MonitorService.class);

    public MonitorService(AssetMonitor assetMonitor)
    {
        this.assetMonitor = assetMonitor;
    }

    @Scheduled(cron = "${monitor.heartbeat}")
    public void monitor()
    {
        this.assetMonitor.trigger();
    }
}
