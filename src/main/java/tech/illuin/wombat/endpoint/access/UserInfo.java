package tech.illuin.wombat.endpoint.access;

import java.time.Instant;
import java.util.List;

public record UserInfo(
    String id,
    String name,
    String email,
    boolean self,
    Instant createdAt,
    Instant lastSeenAt,
    List<RoleInfo> roles,
    List<String> grantableRoles
)
{
    public record RoleInfo(
        String role,
        boolean fromProvider,
        Grant grant,
        boolean removable
    ) {}

    public record Grant(
        String grantedBy,
        Instant grantedAt
    ) {}
}
