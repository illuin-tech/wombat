package tech.illuin.wombat.persistence.backend.api;

import java.util.function.Supplier;

public final class ProcessSupplier extends LazySupplier<Action>
{
    private final String id;
    private final String cron;

    public ProcessSupplier(String id, String cron, Supplier<Action> supplier)
    {
        super(supplier);
        this.id = id;
        this.cron = cron;
    }

    public String id()
    {
        return this.id;
    }

    public String cron()
    {
        return this.cron;
    }
}
