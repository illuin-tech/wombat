package tech.illuin.wombat.security;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.HttpSecurityPolicy;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class AccessPolicy implements HttpSecurityPolicy
{
    static final String NAME = "wombat-access";
    private static final String EMAIL_VERIFIED_CLAIM = "email_verified";

    private static final Logger logger = LoggerFactory.getLogger(AccessPolicy.class);

    private final AuthProperties properties;

    public AccessPolicy(AuthProperties properties)
    {
        this.properties = properties;
    }

    @Override
    public Uni<CheckResult> checkPermission(RoutingContext request, Uni<SecurityIdentity> identity, AuthorizationRequestContext requestContext)
    {
        return identity.map(this::check);
    }

    @Override
    public String name()
    {
        return NAME;
    }

    CheckResult check(SecurityIdentity identity)
    {
        if (identity == null || identity.isAnonymous())
            return CheckResult.DENY;

        if (!this.hasAllowedRole(identity))
        {
            logger.debug("Access denied to {}: none of the roles {} granted", identity.getPrincipal().getName(), this.properties.rolesAllowed().orElseThrow());
            return CheckResult.DENY;
        }
        if (!this.hasAllowedEmail(identity))
        {
            logger.debug("Access denied to {}: email not verified or outside {}", identity.getPrincipal().getName(), this.properties.allowedEmailDomains().orElseThrow());
            return CheckResult.DENY;
        }
        return CheckResult.PERMIT;
    }

    private boolean hasAllowedRole(SecurityIdentity identity)
    {
        return this.properties.rolesAllowed()
            .map(roles -> roles.stream().anyMatch(identity::hasRole))
            .orElse(true);
    }

    private boolean hasAllowedEmail(SecurityIdentity identity)
    {
        if (this.properties.allowedEmailDomains().isEmpty())
            return true;

        if (this.properties.requireVerifiedEmail() && !IdentityClaims.isTrue(identity, EMAIL_VERIFIED_CLAIM))
            return false;

        return IdentityClaims.string(identity, this.properties.emailClaim())
            .map(email -> isInDomains(email, this.properties.allowedEmailDomains().get()))
            .orElse(false);
    }

    private static boolean isInDomains(String email, List<String> domains)
    {
        int at = email.lastIndexOf('@');
        if (at < 0)
            return false;

        String domain = email.substring(at + 1).toLowerCase(Locale.ROOT);
        return domains.stream()
            .map(allowed -> allowed.strip().replaceFirst("^@", "").toLowerCase(Locale.ROOT))
            .anyMatch(domain::equals);
    }
}
