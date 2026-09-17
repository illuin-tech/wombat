package tech.illuin.wombat.persistence.backend.api;

import java.util.function.Supplier;

public final class HookSupplier extends LazySupplier<Action>
{
    private final String id;
    private final HookPhase phase;
    private final int priority;

    public HookSupplier(String id, HookPhase phase, int priority, Supplier<Action> supplier)
    {
        super(supplier);
        this.id = id;
        this.phase = phase;
        this.priority = priority;
    }

    public HookSupplier(String id, HookPhase phase, Supplier<Action> supplier)
    {
        this(id, phase, 0, supplier);
    }

    public String id()
    {
        return this.id;
    }

    public HookPhase phase()
    {
        return this.phase;
    }

    public int priority()
    {
        return this.priority;
    }
}
