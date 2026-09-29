package tech.illuin.wombat.impact.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import tech.illuin.wombat.commons.validation.ValidTimeRange;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.activity.kubernetes.KubernetesActivityData;
import tech.illuin.wombat.core.activity.llm.LLMActivityData;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.module.kubernetes_simulated.KubernetesSimulatedAsset;
import tech.illuin.wombat.module.kubernetes_simulated.KubernetesSimulatedProfile;
import tech.illuin.wombat.module.llm_simulated.LLMSimulatedAsset;
import tech.illuin.wombat.module.llm_simulated.LLMSimulatedProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

public record SimulationRequest(
    @JsonProperty("assets") List<@Valid @NotNull SimulatedAsset> assets,
    @JsonProperty("scopes") Set<Scope> scopes
) {
    public SimulationRequest {
        assets = assets != null ? assets : List.of();
        scopes = scopes != null ? scopes : Set.of(Scope.values());
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = SimulatedLLM.class, name = "LLM"),
        @JsonSubTypes.Type(value = SimulatedKubernetes.class, name = "KUBERNETES")
    })
    public sealed interface SimulatedAsset permits SimulatedLLM, SimulatedKubernetes
    {
        tech.illuin.wombat.core.asset.SimulatedAsset toSimulatedAsset();
    }

    public record SimulatedLLM(
        @NotNull @Valid @JsonProperty("profile") LLMSimulatedProfile profile,
        @NotNull @Valid @JsonProperty("activity") Activity activity
    ) implements SimulatedAsset {
        @Override
        public LLMSimulatedAsset toSimulatedAsset()
        {
            return new LLMSimulatedAsset(this.activity.toActivityData(), this.profile);
        }

        public record Activity(
            @NotNull @ValidTimeRange @JsonProperty("range") TimeRange range,
            @JsonProperty("outputTokenCount") long outputTokenCount,
            @JsonProperty("requestCount") int requestCount
        ) {
            public LLMActivityData toActivityData()
            {
                return new LLMActivityData(
                    ActivityRegime.MODELED,
                    emptySet(),
                    this.range,
                    this.outputTokenCount,
                    this.requestCount
                );
            }
        }
    }

    public record SimulatedKubernetes(
        @NotNull @Valid @JsonProperty("profile") KubernetesSimulatedProfile profile,
        @NotNull @Valid @JsonProperty("activity") Activity activity
    ) implements SimulatedAsset {
        @Override
        public KubernetesSimulatedAsset toSimulatedAsset()
        {
            return new KubernetesSimulatedAsset(this.activity.toActivityData(), this.profile);
        }

        public record Activity(
            @NotNull @ValidTimeRange @JsonProperty("range") TimeRange range,
            @JsonProperty("cpuUsage") double cpuUsage,
            @NotNull @JsonProperty("containerShares") Map<String, @NotNull Double> containerShares
        ) {
            public KubernetesActivityData toActivityData()
            {
                return new KubernetesActivityData(
                    ActivityRegime.MODELED,
                    this.containerShares.keySet(),
                    this.range,
                    this.cpuUsage,
                    this.containerShares,
                    emptyMap()
                );
            }
        }
    }

    public enum Scope
    {
        IMPACT, COST
    }
}
