package tech.illuin.wombat.persistence.micrometer.data;

import io.micrometer.core.instrument.Meter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TagGroup
{
    private final Map<String, String> tags;
    private final int hashCodeCache;

    /**
     * This constructor is purposely private, so we can make sure the tags map is not accessible from the outside, and the hashcode cache remains valid.
     */
    private TagGroup(Map<String, String> tags)
    {
        this.tags = tags;
        this.hashCodeCache = tags.hashCode();
    }

    public static Optional<TagGroup> from(Meter.Id meterId, Set<String> tags)
    {
        Map<String, String> tagMap = new HashMap<>();
        for (String tag : tags)
        {
            String tagValue = meterId.getTag(tag);
            if (tagValue == null)
                return Optional.empty();
            tagMap.put(tag, tagValue);
        }
        return Optional.of(new TagGroup(tagMap));
    }

    public String get(String key)
    {
        return this.tags.get(key);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TagGroup that = (TagGroup) o;

        if (this.hashCodeCache != that.hashCodeCache)
            return false;

        return this.tags.equals(that.tags);
    }

    @Override
    public int hashCode()
    {
        return this.hashCodeCache;
    }
}
