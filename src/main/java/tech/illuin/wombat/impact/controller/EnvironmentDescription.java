package tech.illuin.wombat.impact.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.Environment;
import tech.illuin.wombat.core.activity.commons.TimeRange;

import java.util.List;

public record EnvironmentDescription(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("assets") List<AssetSummary> assets,
    @JsonProperty("time_range") TimeRange timeRange
) {
    public static EnvironmentDescription from(String id, Environment environment)
    {
        return from(id, environment, null);
    }

    public static EnvironmentDescription from(String id, Environment environment, TimeRange timeRange)
    {
        List<AssetSummary> assets = environment.assets().stream().map(AssetSummary::from).toList();
        return new EnvironmentDescription(id, environment.id(), assets, timeRange);
    }

    public record AssetSummary(
        String id,
        String name,
        AssetType type
    ) {
        public static AssetSummary from(Asset properties)
        {
            return new AssetSummary(properties.identity().id(), properties.identity().name(), properties.type());
        }
    }
}
