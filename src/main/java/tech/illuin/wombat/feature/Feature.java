package tech.illuin.wombat.feature;

import tech.illuin.wombat.commons.NodeMode;

import java.util.function.Predicate;

public enum Feature
{
    UI("ui", mode -> mode != NodeMode.WORKER),
    API_READ("api-read", mode -> mode != NodeMode.WORKER),
    API_WRITE("api-write", mode -> mode == NodeMode.AGGREGATOR),
    ADMIN("admin", mode -> mode != NodeMode.WORKER),
    ;

    private final String flag;
    private final Predicate<NodeMode> enabled;

    Feature(String flag, Predicate<NodeMode> enabled)
    {
        this.flag = flag;
        this.enabled = enabled;
    }

    public String flag()
    {
        return this.flag;
    }

    public boolean enabledIn(NodeMode mode)
    {
        return this.enabled.test(mode);
    }
}
