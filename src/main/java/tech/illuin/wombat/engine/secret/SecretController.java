package tech.illuin.wombat.engine.secret;

import io.quarkus.resteasy.reactive.server.EndpointDisabled;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
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
import tech.illuin.wombat.engine.secret.audit.AuditableOperation;
import tech.illuin.wombat.engine.secret.audit.AuditableOutcome;
import tech.illuin.wombat.engine.secret.audit.SecretAuditService;
import tech.illuin.wombat.security.Permission;

import java.util.List;

import static tech.illuin.wombat.engine.secret.audit.AuditableOperation.*;
import static tech.illuin.wombat.engine.secret.audit.AuditableOutcome.*;

@ApplicationScoped
@Path("api/secrets")
@EndpointDisabled(name = "wombat.secret.write-mode", stringValue = "DISABLED", disableIfMissing = true)
@PermissionsAllowed(Permission.Names.API_SECRET_MANAGE)
@Blocking
public class SecretController
{
    private final SecretWriter secrets;
    private final SecurityIdentity identity;
    private final SecretAuditService auditService;

    public SecretController(SecretWriter secrets, SecurityIdentity identity, SecretAuditService auditService)
    {
        this.secrets = secrets;
        this.identity = identity;
        this.auditService = auditService;
    }

    @GET @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", type = SchemaType.ARRAY, implementation = SecretMetadata.class)
    })))
    public Response<List<SecretMetadata>> list()
    {
        List<SecretMetadata> metadata = this.secrets.listSecrets();
        this.auditService.audit(this.identity, list, success);
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
        AuditableOutcome outcome = failure;
        try {
            this.secrets.putSecret(request.key(), request.value());
            outcome = success;
            return Response.status("Secret key was successfully saved");
        }
        finally {
            this.auditService.audit(this.identity, put, outcome);
        }
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class)
    })))
    public Response<Void> delete(@QueryParam("key") @NotBlank String key)
    {
        AuditableOutcome outcome = failure;
        try {
            if (!this.secrets.deleteSecret(key))
            {
                outcome = not_found;
                throw new NotFoundException("Requested secret " + key + " could not be found");
            }
            outcome = success;
            return Response.status("Secret key was successfully deleted");
        }
        finally {
            this.auditService.audit(this.identity, delete, outcome);
        }
    }
}
