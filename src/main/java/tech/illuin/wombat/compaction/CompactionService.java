package tech.illuin.wombat.compaction;

import io.quarkus.scheduler.Scheduled;

import java.time.Instant;

public class CompactionService
{
    private final MetricCompactor compactor;

    public CompactionService(MetricCompactor compactor)
    {
        this.compactor = compactor;
    }

    @Scheduled(cron = "0 1 * * * ?")
    public void compact()
    {
        this.compactor.compact(Instant.now());
    }
}
