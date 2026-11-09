package tech.illuin.wombat.security;

import io.quarkus.security.identity.SecurityIdentity;

import java.security.Principal;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public record Caller(
    String issuer,
    String subject,
    String name,
    String email,
    Set<Role> roles,
    Set<Permission> permissions
)
{
    public static final String ROLES_ATTRIBUTE = "wombat-roles";
    public static final String PERMISSIONS_ATTRIBUTE = "wombat-permissions";
    public static final String API_KEY_ATTRIBUTE = "wombat-api-key";

    public static Caller from(SecurityIdentity identity, AuthProperties properties)
    {
        return from(identity, properties, roles(identity), permissions(identity));
    }

    /** With roles mapped by the caller, for identities that are not augmented yet. */
    public static Caller from(SecurityIdentity identity, AuthProperties properties, Set<Role> roles)
    {
        return from(identity, properties, roles, Role.permissionsOf(roles));
    }

    private static Caller from(SecurityIdentity identity, AuthProperties properties, Set<Role> roles, Set<Permission> permissions)
    {
        String subject = subject(identity);
        String name = properties.displayNameClaims().stream()
            .map(claim -> IdentityClaims.string(identity, claim))
            .flatMap(Optional::stream)
            .findFirst()
            .orElse(subject);

        return new Caller(
            issuer(identity),
            subject,
            name,
            IdentityClaims.string(identity, properties.emailClaim()).orElse(null),
            roles,
            permissions
        );
    }

    public boolean has(Permission permission)
    {
        return this.permissions.contains(permission);
    }

    public static String issuer(SecurityIdentity identity)
    {
        return IdentityClaims.string(identity, "iss").orElse("");
    }

    public static String subject(SecurityIdentity identity)
    {
        return IdentityClaims.string(identity, "sub").orElseGet(() -> Optional.ofNullable(identity.getPrincipal())
            .map(Principal::getName)
            .filter(name -> !name.isBlank())
            .orElse("anonymous"));
    }

    public static Set<Role> roles(SecurityIdentity identity)
    {
        Set<Role> roles = identity.getAttribute(ROLES_ATTRIBUTE);
        return roles == null ? EnumSet.noneOf(Role.class) : roles;
    }

    public static Set<Permission> permissions(SecurityIdentity identity)
    {
        Set<Permission> permissions = identity.getAttribute(PERMISSIONS_ATTRIBUTE);
        return permissions == null ? EnumSet.noneOf(Permission.class) : permissions;
    }

    public static boolean isApiKey(SecurityIdentity identity)
    {
        return identity.getAttribute(API_KEY_ATTRIBUTE) != null;
    }
}
