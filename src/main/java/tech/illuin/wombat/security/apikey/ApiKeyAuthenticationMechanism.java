package tech.illuin.wombat.security.apikey;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import tech.illuin.wombat.security.AuthProperties;

import java.util.Set;

@ApplicationScoped
public class ApiKeyAuthenticationMechanism implements HttpAuthenticationMechanism
{
    public static final String HEADER = "X-API-Key";
    private static final int PRIORITY = DEFAULT_PRIORITY + 1000;

    private final AuthProperties properties;

    public ApiKeyAuthenticationMechanism(AuthProperties properties)
    {
        this.properties = properties;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager)
    {
        String token = this.apiKey(context);
        if (token == null)
            return Uni.createFrom().nullItem();
        return identityProviderManager.authenticate(new ApiKeyAuthenticationRequest(token));
    }

    /* Only answers for requests carrying a key, so the provider keeps its own challenge (login redirect) for the others. */
    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context)
    {
        if (this.apiKey(context) == null)
            return Uni.createFrom().nullItem();
        return Uni.createFrom().item(new ChallengeData(401, null, null));
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes()
    {
        return Set.of(ApiKeyAuthenticationRequest.class);
    }

    @Override
    public int getPriority()
    {
        return PRIORITY;
    }

    private String apiKey(RoutingContext context)
    {
        if (!this.properties.enabled())
            return null;
        return headerApiKey(context.request().getHeader(HEADER));
    }

    static String headerApiKey(String header)
    {
        if (header == null || header.isBlank())
            return null;
        return header.strip();
    }
}
