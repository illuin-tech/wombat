package tech.illuin.wombat.security.apikey;

import io.quarkus.oidc.OidcRequestContext;
import io.quarkus.oidc.OidcTenantConfig;
import io.quarkus.oidc.TenantConfigResolver;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import tech.illuin.wombat.security.AuthProperties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static io.quarkus.oidc.runtime.OidcTenantConfig.ApplicationType.SERVICE;

/* Routes bearer tokens issued by Wombat to a local tenant verifying them with Wombat's public key; everything else goes to the identity provider. */
@ApplicationScoped
public class ApiKeyTenantResolver implements TenantConfigResolver
{
    public static final String TENANT_ID = "wombat-api-key";
    private static final String BEARER = "Bearer ";

    private final AuthProperties properties;
    private final OidcTenantConfig tenantConfig;

    public ApiKeyTenantResolver(AuthProperties properties, ApiKeySigningKey signingKey)
    {
        this.properties = properties;
        this.tenantConfig = OidcTenantConfig.builder()
            .tenantId(TENANT_ID)
            .applicationType(SERVICE)
            .discoveryEnabled(false)
            .publicKey(signingKey.publicKey())
            .token()
                .issuer(ApiKeySigningKey.ISSUER)
                .audience(ApiKeySigningKey.AUDIENCE)
                .end()
            .build();
    }

    @Override
    public Uni<OidcTenantConfig> resolve(RoutingContext context, OidcRequestContext<OidcTenantConfig> requestContext)
    {
        if (!this.properties.enabled() || !isIssuedByWombat(context.request().getHeader(HttpHeaders.AUTHORIZATION)))
            return Uni.createFrom().nullItem();
        return Uni.createFrom().item(this.tenantConfig);
    }

    static boolean isIssuedByWombat(String authorization)
    {
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length()))
            return false;

        String[] parts = authorization.substring(BEARER.length()).strip().split("\\.");
        if (parts.length != 3)
            return false;

        try {
            JsonObject claims = new JsonObject(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            return ApiKeySigningKey.ISSUER.equals(claims.getValue("iss"));
        }
        catch (IllegalArgumentException | DecodeException e) {
            return false;
        }
    }
}
