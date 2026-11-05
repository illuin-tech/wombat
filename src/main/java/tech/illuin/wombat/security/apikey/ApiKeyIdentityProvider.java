package tech.illuin.wombat.security.apikey;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import tech.illuin.wombat.security.Caller;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.Role;

import java.util.EnumSet;
import java.util.stream.Collectors;

/** Turns an API key into an identity holding the key's permissions and no role, so it never passes for a user. */
@ApplicationScoped
public class ApiKeyIdentityProvider implements IdentityProvider<ApiKeyAuthenticationRequest>
{
    private final ApiKeyService apiKeyService;

    public ApiKeyIdentityProvider(ApiKeyService apiKeyService)
    {
        this.apiKeyService = apiKeyService;
    }

    @Override
    public Class<ApiKeyAuthenticationRequest> getRequestType()
    {
        return ApiKeyAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(ApiKeyAuthenticationRequest request, AuthenticationRequestContext context)
    {
        return context.runBlocking(() -> {
            ApiKeyService.Grant grant = this.apiKeyService.authenticate(request.token())
                .orElseThrow(() -> new AuthenticationFailedException("Unknown, revoked or expired API key"));

            return QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(grant.principal()))
                .addAttribute(Caller.API_KEY_ATTRIBUTE, grant.keyId())
                .addAttribute(Caller.ROLES_ATTRIBUTE, EnumSet.noneOf(Role.class))
                .addAttribute(Caller.PERMISSIONS_ATTRIBUTE, grant.permissions())
                .addPermissionsAsString(grant.permissions().stream().map(Permission::value).collect(Collectors.toSet()))
                .build();
        });
    }
}
