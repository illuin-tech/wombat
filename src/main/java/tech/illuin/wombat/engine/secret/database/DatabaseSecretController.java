package tech.illuin.wombat.engine.secret.database;

import io.quarkus.resteasy.reactive.server.EndpointDisabled;
import io.quarkus.security.PermissionsAllowed;
import io.quarkus.security.identity.SecurityIdentity;
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
import tech.illuin.wombat.engine.secret.audit.AuditableOutcome;
import tech.illuin.wombat.engine.secret.audit.SecretAuditService;
import tech.illuin.wombat.engine.secret.database.crypto.SecretRewrap;
import tech.illuin.wombat.engine.secret.database.crypto.SecretRewrapService;
import tech.illuin.wombat.security.Permission;

import static tech.illuin.wombat.engine.secret.audit.AuditableOperation.rewrap;
import static tech.illuin.wombat.engine.secret.audit.AuditableOutcome.*;

@ApplicationScoped
@Path("api/secrets/database")
@EndpointDisabled(name = "wombat.secret.write-mode", stringValue = "DISABLED", disableIfMissing = true)
@PermissionsAllowed(Permission.Names.API_SECRET_MANAGE)
@Blocking
public class DatabaseSecretController
{
    private final SecretRewrapService rewrapService;
    private final SecurityIdentity identity;
    private final SecretAuditService auditService;

    public DatabaseSecretController(SecretRewrapService rewrapService, SecurityIdentity identity, SecretAuditService auditService)
    {
        this.rewrapService = rewrapService;
        this.identity = identity;
        this.auditService = auditService;
    }

    @POST @Path("rewrap")
    @Produces(MediaType.APPLICATION_JSON)
    @APIResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(properties = {
        @SchemaProperty(name = "status", implementation = Status.class),
        @SchemaProperty(name = "payload", implementation = SecretRewrap.class)
    })))
    public Response<SecretRewrap> rewrap()
    {
        AuditableOutcome outcome = failure;
        try {
            SecretRewrap result = this.rewrapService.rewrapBatch();
            outcome = result.complete() && result.failures() == 0 && result.conflicts() == 0 ? success : partial_success;
            return Response.success(result);
        }
        finally {
            this.auditService.audit(this.identity, rewrap, outcome);
        }
    }
}
