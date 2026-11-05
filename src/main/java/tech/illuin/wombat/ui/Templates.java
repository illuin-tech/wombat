package tech.illuin.wombat.ui;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.engine.impact.timeline.ImpactTimeline;

import java.util.List;

@CheckedTemplate(requireTypeSafeExpressions = false)
public class Templates
{

    public static native TemplateInstance impact(
        EnvironmentImpact env,
        AssetSelection assets,
        EnvironmentSelection environments,
        MaxSpan maxSpan,
        ImpactTimeline timeline
    );

    public static native TemplateInstance impactError(TimeRange timeRange, MaxSpan maxSpan);

    public static native TemplateInstance loggedOut(boolean idpSessionEnded);

    public static native TemplateInstance apiKeys(ApiKeysPage page);

    public static native TemplateInstance users(UsersPage page);

    public record MaxSpan(long millis, String label) {}

    /** {@code enabled} is false without authentication, where {@code /api/keys} is disabled. */
    public record ApiKeysPage(boolean enabled, List<String> permissions, long maxLifetimeDays, boolean allowNeverExpiring) {}

    /** {@code enabled} is false without authentication, where no user is ever recorded; {@code canManage} shows the role actions. */
    public record UsersPage(boolean enabled, boolean canManage, List<UserView> users) {}

    /** {@code grantable} lists the roles an admin can still grant this user. */
    public record UserView(String id, String name, String email, boolean self, List<RoleView> roles, List<String> grantable, String firstSeen, String lastSeen) {}

    /** {@code granted} is true for a role granted in Wombat, which an admin can remove when {@code removable}; {@code source} explains where it comes from. */
    public record RoleView(String value, boolean granted, boolean removable, String source) {}

    public record AssetSelection(List<Asset> all, List<String> selectedIds) {}

    public record EnvironmentView(String id, String name) {}

    public record EnvironmentSelection(List<EnvironmentView> options, String selectedId) {}
}
