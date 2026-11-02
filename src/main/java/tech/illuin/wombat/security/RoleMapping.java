package tech.illuin.wombat.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Maps a signed-in user to Wombat roles. Each {@code auth.roles.<role>} entry is either an identity provider role or group,
 * an email (any entry containing {@code @}, for providers without roles such as Google), or {@code "*"} for everyone.
 */
@ApplicationScoped
public class RoleMapping
{
    static final String ANY_ROLE = "*";
    private static final String EMAIL_VERIFIED_CLAIM = "email_verified";

    private final AuthProperties properties;

    public RoleMapping(AuthProperties properties)
    {
        this.properties = properties;
    }

    public Set<Role> map(SecurityIdentity identity)
    {
        return this.map(identity.getRoles(), this.verifiedEmail(identity));
    }

    Set<Role> map(Collection<String> providerRoles, Optional<String> email)
    {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        if (grants(this.properties.roles().admin(), providerRoles, email))
            roles.add(Role.ADMIN);
        if (grants(this.properties.roles().user(), providerRoles, email))
            roles.add(Role.USER);
        return roles;
    }

    /** The caller's email, only when the provider flagged it verified (unless {@code auth.require-verified-email} is off). */
    private Optional<String> verifiedEmail(SecurityIdentity identity)
    {
        if (this.properties.requireVerifiedEmail() && !IdentityClaims.isTrue(identity, EMAIL_VERIFIED_CLAIM))
            return Optional.empty();
        return IdentityClaims.string(identity, this.properties.emailClaim());
    }

    private static boolean grants(Optional<List<String>> mapped, Collection<String> providerRoles, Optional<String> email)
    {
        return mapped
            .map(candidates -> candidates.stream()
                .map(String::strip)
                .anyMatch(candidate -> ANY_ROLE.equals(candidate) || matches(candidate, providerRoles, email)))
            .orElse(false);
    }

    private static boolean matches(String candidate, Collection<String> providerRoles, Optional<String> email)
    {
        if (candidate.contains("@"))
            return email.map(value -> value.strip().toLowerCase(Locale.ROOT).equals(candidate.toLowerCase(Locale.ROOT))).orElse(false);
        return providerRoles.contains(candidate);
    }
}
