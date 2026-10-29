package tech.illuin.wombat.endpoint;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import tech.illuin.wombat.endpoint.validation.ValidTimeRange;
import tech.illuin.wombat.core.activity.commons.TimeRange;
import tech.illuin.wombat.core.activity.kubernetes.KubernetesActivityData;
import tech.illuin.wombat.core.activity.llm.LLMActivityData;
import tech.illuin.wombat.core.activity.llm.LLMServiceActivity;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.evaluation.impact.kubernetes.ClusterInfo;
import tech.illuin.wombat.module.kubernetes_simulated.KubernetesSimulatedAsset;
import tech.illuin.wombat.module.kubernetes_simulated.KubernetesSimulatedProfile;
import tech.illuin.wombat.module.llm_simulated.LLMSimulatedAsset;
import tech.illuin.wombat.module.llm_simulated.LLMSimulatedProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    @Schema(
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "LLM", schema = SimulatedLLM.class),
            @DiscriminatorMapping(value = "KUBERNETES", schema = SimulatedKubernetes.class)
        },
        oneOf = { SimulatedLLM.class, SimulatedKubernetes.class }
    )
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
            return new LLMSimulatedAsset(this.activity.toActivityData(this.profile), this.profile);
        }

        public record Activity(
            @NotNull @ValidTimeRange @JsonProperty("range") TimeRange range,
            @JsonProperty("outputTokenPerRequest") long outputTokenPerRequest,
            @JsonProperty("requestCount") int requestCount
        ) {
            public LLMActivityData toActivityData(LLMSimulatedProfile profile)
            {
                String serviceId = profile.model();
                LLMServiceActivity serviceActivity = new LLMServiceActivity(
                    profile.provider(),
                    profile.model(),
                    profile.location(),
                    this.outputTokenPerRequest * this.requestCount,
                    this.requestCount
                );
                return new LLMActivityData(
                    ActivityRegime.MODELED,
                    Set.of(serviceId),
                    this.range,
                    Map.of(serviceId, serviceActivity)
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
                    this.containerShares.keySet().stream().collect(Collectors.toMap(
                        container -> container,
                        _ -> new ClusterInfo("simulated-cluster", "simulated-namespace")
                    ))
                );
            }
        }
    }

    public enum Scope
    {
        IMPACT, COST
    }
}
