package tech.illuin.wombat.ui;

import io.quarkus.oidc.IdTokenCredential;
import io.quarkus.oidc.OidcSession;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("logout")
public class SessionController
{
    private final OidcSession oidcSession;
    private final SecurityIdentity identity;

    public SessionController(OidcSession oidcSession, SecurityIdentity identity)
    {
        this.oidcSession = oidcSession;
        this.identity = identity;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Uni<TemplateInstance> logout()
    {
        if (this.identity.getCredential(IdTokenCredential.class) == null)
            return Uni.createFrom().item(Templates.loggedOut());

        return this.oidcSession.logout().replaceWith(() -> Templates.loggedOut());
    }
}
