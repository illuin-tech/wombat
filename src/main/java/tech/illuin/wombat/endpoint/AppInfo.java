package tech.illuin.wombat.endpoint;

public record AppInfo(
    Auth auth,
    CurrentUser user,
    Ui ui
)
{
    public record Auth(boolean enabled) {}

    public record Ui(MaxDateRange maxDateRange) {}

    public record MaxDateRange(long millis, String label) {}
}
