package tech.illuin.wombat.commons;

public enum ConfigSourceFactories
{
    NODE_MODE(9001),
    BACKEND_MODE(9000),
    FEATURE_FLAG(9000),
    ;

    private final int priority;

    ConfigSourceFactories(int priority)
    {
        this.priority = priority;
    }

    public int priority()
    {
        return this.priority;
    }
}
