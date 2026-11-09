package tech.illuin.wombat.endpoint.openapi.filter;

import io.quarkus.smallrye.openapi.OpenApiFilter;
import io.smallrye.config.SmallRyeConfig;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import tech.illuin.wombat.engine.secret.SecretProperties;

@OpenApiFilter(stages = OpenApiFilter.RunStage.RUNTIME_STARTUP)
public class SecretOpenApiFilter implements OASFilter
{
    @Override
    public void filterOpenAPI(OpenAPI openAPI)
    {
        SmallRyeConfig config = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        SecretProperties.WriteMode mode = config.getConfigMapping(SecretProperties.class).writeMode();

        if (mode == SecretProperties.WriteMode.disabled)
        {
            if (openAPI.getPaths() != null)
                openAPI.getPaths().removePathItem("/api/secrets");
            removeSchema(openAPI, "SecretWriteRequest");
            removeSchema(openAPI, "SecretMetadata");
        }
        if (mode != SecretProperties.WriteMode.database)
        {
            if (openAPI.getPaths() != null)
                openAPI.getPaths().removePathItem("/api/secrets/database/rewrap");
            removeSchema(openAPI, "SecretRewrap");
        }
    }

    private static void removeSchema(OpenAPI openAPI, String name)
    {
        if (openAPI.getComponents() != null)
            openAPI.getComponents().removeSchema(name);
    }
}
