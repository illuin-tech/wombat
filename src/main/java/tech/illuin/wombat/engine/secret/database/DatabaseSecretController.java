package tech.illuin.wombat.engine.secret.database;

import io.quarkus.resteasy.reactive.server.EndpointDisabled;
import io.quarkus.security.PermissionsAllowed;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.engine.secret.database.crypto.SecretRewrap;
import tech.illuin.wombat.engine.secret.database.crypto.SecretRewrapService;
import tech.illuin.wombat.security.Permission;

@ApplicationScoped
@Path("api/secrets/database")
@EndpointDisabled(name = "wombat.secret.write-mode", stringValue = "DISABLED", disableIfMissing = true)
@PermissionsAllowed(Permission.Names.API_SECRET_MANAGE)
@Blocking
public class DatabaseSecretController
{
    private final SecretRewrapService rewrapService;

    public DatabaseSecretController(SecretRewrapService rewrapService)
    {
        this.rewrapService = rewrapService;
    }

    @POST @Path("rewrap")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = SecretRewrap.class)
    })))
    public Response<SecretRewrap> rewrap()
    {
        SecretRewrap result = this.rewrapService.rewrapBatch();
        return Response.success(result);
    }
}
