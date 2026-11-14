package tech.illuin.wombat.persistence.compaction;

import io.quarkus.scheduler.Scheduled;

import java.time.Instant;

public class CompactionService
{
    private final MetricCompactor compactor;

    public CompactionService(MetricCompactor compactor)
    {
        this.compactor = compactor;
    }

    @Scheduled(cron = "${persistence.compaction.cron}")
    public void compact()
    {
        this.compactor.compact(Instant.now());
    }
}
