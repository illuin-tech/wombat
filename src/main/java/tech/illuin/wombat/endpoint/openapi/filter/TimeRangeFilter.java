package tech.illuin.wombat.endpoint.openapi.filter;

import io.quarkus.smallrye.openapi.OpenApiFilter;
import org.eclipse.microprofile.openapi.OASFilter;

import java.util.Set;

@OpenApiFilter(stages = OpenApiFilter.RunStage.BUILD)
public class TimeRangeFilter extends PropertyBasedFilter implements OASFilter
{
    public TimeRangeFilter()
    {
        super("TimeRange", Set.of("start", "end", "resolution"));
    }
}
