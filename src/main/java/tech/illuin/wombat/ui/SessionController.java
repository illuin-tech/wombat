package tech.illuin.wombat.ui;

import io.quarkus.oidc.IdTokenCredential;
import io.quarkus.oidc.OidcConfigurationMetadata;
import io.quarkus.oidc.OidcSession;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.illuin.wombat.security.AuthProperties;

import java.net.URI;
import java.util.Optional;

@Path("logout")
public class SessionController
{
    /** {@code io.quarkus.oidc.runtime.OidcUtils#CONFIG_METADATA_ATTRIBUTE} */
    private static final String CONFIG_METADATA_ATTRIBUTE = "configuration-metadata";
    private static final String POST_LOGOUT_PATH = "logout/done";

    private final OidcSession oidcSession;
    private final SecurityIdentity identity;
    private final AuthProperties properties;
    private final Optional<String> clientId;

    public SessionController(
        OidcSession oidcSession,
        SecurityIdentity identity,
        AuthProperties properties,
        @ConfigProperty(name = "quarkus.oidc.client-id") Optional<String> clientId
    ) {
        this.oidcSession = oidcSession;
        this.identity = identity;
        this.properties = properties;
        this.clientId = clientId;
    }

    /**
     * Clears the local session, then ends the identity provider session (RP-initiated logout) when the provider supports it.
     * Providers without an {@code end_session_endpoint} (e.g. Google) only get the local session cleared.
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Uni<Response> logout(@Context UriInfo uriInfo)
    {
        IdTokenCredential idToken = this.identity.getCredential(IdTokenCredential.class);
        if (idToken == null)
            return Uni.createFrom().item(Response.ok(Templates.loggedOut(false)).build());

        Optional<URI> endSession = this.properties.idpLogout() ? this.endSessionUri(idToken, uriInfo) : Optional.empty();
        return this.oidcSession.logout().map(ignored -> endSession
            .map(uri -> Response.seeOther(uri).build())
            .orElseGet(() -> Response.ok(Templates.loggedOut(false)).build())
        );
    }

    /** Landing page the identity provider redirects to once its session is ended. */
    @GET
    @Path("done")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance loggedOut()
    {
        return Templates.loggedOut(true);
    }

    private Optional<URI> endSessionUri(IdTokenCredential idToken, UriInfo uriInfo)
    {
        OidcConfigurationMetadata metadata = this.identity.getAttribute(CONFIG_METADATA_ATTRIBUTE);
        if (metadata == null || metadata.getEndSessionUri() == null)
            return Optional.empty();

        UriBuilder builder = UriBuilder.fromUri(metadata.getEndSessionUri())
            .queryParam("id_token_hint", idToken.getToken())
            .queryParam("post_logout_redirect_uri", uriInfo.getBaseUriBuilder().path(POST_LOGOUT_PATH).build());
        this.clientId.ifPresent(id -> builder.queryParam("client_id", id));
        return Optional.of(builder.build());
    }
}
