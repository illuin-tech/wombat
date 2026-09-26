package tech.illuin.wombat.impact.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import tech.illuin.wombat.core.activity.commons.TimeRange;

import java.util.List;
import java.util.Set;

public record ImpactRequest(
    @JsonProperty("source_time_range") TimeRange sourceTimeRange,
    @JsonProperty("environments") List<@Valid Environment> environments
) {
    public ImpactRequest {
        environments = environments != null ? environments : List.of();
    }

    public record Environment(
        @NotBlank @JsonProperty("id") String id,
        @JsonProperty("assets") List<@Valid Asset> assets
    ) {
        public Environment {
            assets = assets != null ? assets : List.of();
        }
    }

    public record Asset(
        @NotBlank @JsonProperty("id") String id,
        @JsonProperty("service_ids") Set<String> serviceIds
    ) {
        public Asset {
            serviceIds = serviceIds != null ? serviceIds : Set.of();
        }
    }
}
