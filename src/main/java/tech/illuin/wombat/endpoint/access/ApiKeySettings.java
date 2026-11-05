package tech.illuin.wombat.endpoint.access;

import java.util.List;

public record ApiKeySettings(
    List<String> grantablePermissions,
    long maxLifetimeDays,
    boolean allowNeverExpiring
) {}
