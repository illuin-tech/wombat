package tech.illuin.wombat.engine.secret;

import io.quarkus.resteasy.reactive.server.EndpointDisabled;
import io.quarkus.security.PermissionsAllowed;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import tech.illuin.wombat.core.secret.SecretResolver;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.engine.module.registry.schema.DefaultSchemaRegistry;
import tech.illuin.wombat.feature.Feature;
import tech.illuin.wombat.security.Permission;

/**
 * Tells whether a secret key resolves, so that an asset can be declared with a key known to the configured secret
 * resolvers; unlike {@link SecretController}, it is available whatever the write mode, and never discloses a value.
 */
@ApplicationScoped
@Path("api/secret-keys")
@EndpointDisabled(name = Feature.Keys.API_READ, stringValue = "false")
@PermissionsAllowed(Permission.Names.API_SECRET_READ)
@Blocking
public class SecretKeyController
{
    private final SecretResolver resolver;

    public SecretKeyController(SecretResolver resolver)
    {
        this.resolver = resolver;
    }

    @GET
    @Path("/check")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = SecretKeyCheck.class)
    })))
    @APIResponse(responseCode = "400", description = "The key is blank or is not a valid secret key")
    public Response<SecretKeyCheck> check(@QueryParam("key") @NotBlank @Pattern(regexp = DefaultSchemaRegistry.SECRET_KEY_PATTERN) String key)
    {
        return Response.success(new SecretKeyCheck(key, this.resolver.find(key).isPresent()));
    }

    public record SecretKeyCheck(String key, boolean exists) {}
}
