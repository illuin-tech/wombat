package tech.illuin.wombat.engine.secret;

import io.quarkus.resteasy.reactive.server.EndpointDisabled;
import io.quarkus.security.PermissionsAllowed;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import tech.illuin.wombat.core.secret.SecretMetadata;
import tech.illuin.wombat.core.secret.SecretWriter;
import tech.illuin.wombat.endpoint.response.Response;
import tech.illuin.wombat.endpoint.response.Status;
import tech.illuin.wombat.security.Permission;

import java.util.List;

@ApplicationScoped
@Path("api/secrets")
@EndpointDisabled(name = "wombat.secret.write-mode", stringValue = "DISABLED", disableIfMissing = true)
@PermissionsAllowed(Permission.Names.API_SECRET_MANAGE)
@Blocking
public class SecretController
{
    private final SecretWriter secrets;

    public SecretController(SecretWriter secrets)
    {
        this.secrets = secrets;
    }

    @GET @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", type = SchemaType.ARRAY, implementation = SecretMetadata.class)
    })))
    public Response<List<SecretMetadata>> list()
    {
        List<SecretMetadata> metadata = this.secrets.listSecrets();
        return Response.success(metadata);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class)
    })))
    public Response<Void> put(@Valid SecretWriteRequest request)
    {
        this.secrets.putSecret(request.key(), request.value());
        return Response.status("Secret key was successfully saved");
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class)
    })))
    public Response<Void> delete(@QueryParam("key") @NotBlank String key)
    {
        if (!this.secrets.deleteSecret(key))
            throw new NotFoundException("Requested secret " + key + " could not be found");

        return Response.status("Secret key was successfully deleted");
    }
}
