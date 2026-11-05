package tech.illuin.wombat.security;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Maps a signed-in user to Wombat roles. Each {@code auth.roles.<role>} entry is either an identity provider role or group,
 * an email (any entry containing {@code @}, for providers without roles such as Google), or {@code "*"} for everyone.
 */
@Startup
@ApplicationScoped
public class RoleMapping
{
    static final String ANY_ROLE = "*";
    private static final String EMAIL_VERIFIED_CLAIM = "email_verified";

    private final AuthProperties properties;
    private final Map<Role, List<String>> mapped;

    public RoleMapping(AuthProperties properties)
    {
        this.properties = properties;
        this.mapped = byRole(properties.roles());
    }

    /*
     * Keys are matched loosely because env variables lose the dashes: AUTH_ROLES_API_KEY_MANAGER arrives as
     * "api.key.manager". A misspelt key fails startup, since it would otherwise map nobody without any sign of it.
     */
    static Map<Role, List<String>> byRole(Map<String, List<String>> configured)
    {
        Map<Role, List<String>> byRole = new EnumMap<>(Role.class);
        List<String> unknown = new ArrayList<>();
        configured.forEach((name, candidates) -> Role.fromValue(name.toLowerCase(Locale.ROOT).replace('.', '-').replace('_', '-'))
            .ifPresentOrElse(role -> byRole.put(role, candidates), () -> unknown.add(name)));
        if (!unknown.isEmpty())
            throw new IllegalStateException("Unknown role in auth.roles: " + unknown.stream().sorted().toList() + ", expected one of " + Arrays.stream(Role.values()).map(Role::value).toList());
        return byRole;
    }

    public Set<Role> map(SecurityIdentity identity)
    {
        return this.map(identity.getRoles(), this.verifiedEmail(identity));
    }

    Set<Role> map(Collection<String> providerRoles, Optional<String> email)
    {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (Role role : Role.values())
            if (grants(this.mapped.get(role), providerRoles, email))
                roles.add(role);
        return roles;
    }

    /** The caller's email, only when the provider flagged it verified (unless {@code auth.require-verified-email} is off). */
    private Optional<String> verifiedEmail(SecurityIdentity identity)
    {
        if (this.properties.requireVerifiedEmail() && !IdentityClaims.isTrue(identity, EMAIL_VERIFIED_CLAIM))
            return Optional.empty();
        return IdentityClaims.string(identity, this.properties.emailClaim());
    }

    private static boolean grants(List<String> mapped, Collection<String> providerRoles, Optional<String> email)
    {
        return mapped != null && mapped.stream()
            .map(String::strip)
            .anyMatch(candidate -> ANY_ROLE.equals(candidate) || matches(candidate, providerRoles, email));
    }

    private static boolean matches(String candidate, Collection<String> providerRoles, Optional<String> email)
    {
        if (candidate.contains("@"))
            return email.map(value -> value.strip().toLowerCase(Locale.ROOT).equals(candidate.toLowerCase(Locale.ROOT))).orElse(false);
        return providerRoles.contains(candidate);
    }
}
