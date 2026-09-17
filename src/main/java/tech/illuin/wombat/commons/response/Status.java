package tech.illuin.wombat.commons.response;

import java.util.HashMap;
import java.util.Map;

public record Status(String message, Map<String, Object> metadata)
{
    public Status(String message)
    {
        this(message, new HashMap<>());
    }

    public Status set(String key, Object value)
    {
        this.metadata.put(key, value);
        return this;
    }
}
