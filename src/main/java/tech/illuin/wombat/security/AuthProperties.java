package tech.illuin.wombat.security;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

/**
 * Provider-agnostic access rules applied on top of the OIDC authentication (only relevant when {@code quarkus.oidc.tenant-enabled} is true).
 */
@ConfigMapping(prefix = "auth")
public interface AuthProperties
{
    /** When set, the caller must hold at least one of these roles (see {@code quarkus.oidc.roles.role-claim-path}). */
    Optional<List<String>> rolesAllowed();

    /** When set, the caller's email must belong to one of these domains (e.g. {@code example.com}). */
    Optional<List<String>> allowedEmailDomains();

    @WithDefault("email")
    String emailClaim();

    /** Rejects emails not flagged by the {@code email_verified} claim when checking {@link #allowedEmailDomains()}. */
    @WithDefault("true")
    boolean requireVerifiedEmail();

    /** Claims tried in order to display the signed-in user, falling back to the principal name. */
    @WithDefault("email,name,preferred_username")
    List<String> displayNameClaims();

    /** Ends the identity provider session on logout when the provider advertises an {@code end_session_endpoint}. */
    @WithDefault("true")
    boolean idpLogout();
}
