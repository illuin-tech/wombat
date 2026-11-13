package tech.illuin.wombat.persistence.backend.api.store;

public record SkippedMetrics(String assetId, String assetType, String reason, int count)
{
    public static final String STATUS_KEY = "errors";
}
