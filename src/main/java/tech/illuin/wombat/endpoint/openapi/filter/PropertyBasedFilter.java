package tech.illuin.wombat.endpoint.openapi.filter;

import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.media.Schema;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public abstract class PropertyBasedFilter implements OASFilter
{
    private final String schemaName;
    private final Set<String> allowedProperties;

    protected PropertyBasedFilter(String schemaName, Collection<String> allowedProperties)
    {
        this.schemaName = schemaName;
        this.allowedProperties = Set.copyOf(allowedProperties);
    }

    @Override
    public void filterOpenAPI(OpenAPI openAPI)
    {
        if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null)
            return;

        Schema timeRangeSchema = openAPI.getComponents().getSchemas().get(this.schemaName);
        if (timeRangeSchema != null && timeRangeSchema.getProperties() != null)
        {
            Map<String, Schema> properties = new HashMap<>(timeRangeSchema.getProperties());
            for (String prop : timeRangeSchema.getProperties().keySet())
            {
                if (!this.allowedProperties.contains(prop))
                    properties.remove(prop);
            }
            timeRangeSchema.setProperties(properties);
        }
    }
}
