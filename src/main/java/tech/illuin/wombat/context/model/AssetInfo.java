package tech.illuin.wombat.context.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.illuin.wombat.context.persistence.AssetEntity;
import tech.illuin.wombat.core.asset.AssetType;
import tech.illuin.wombat.module.kubernetes_api.KubernetesAPIAsset;
import tech.illuin.wombat.module.llm_prometheus.LLMPrometheusAsset;
import tech.illuin.wombat.module.llm_static.LLMStaticAsset;

import java.time.Duration;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssetInfo(
    @JsonProperty("id") String id,
    @JsonProperty("uuid") String uuid,
    @JsonProperty("name") String name,
    @JsonProperty("type") AssetType type,
    @JsonProperty("config-path") String configPath,
    @JsonProperty("namespace") String namespace,
    @JsonProperty("context") String context,
    @JsonProperty("read-timeout") Duration readTimeout,
    @JsonProperty("prometheus-url") String prometheusUrl,
    @JsonProperty("proxy-url") String proxyUrl,
    @JsonProperty("username") String username,
    @JsonProperty("heartbeat-skip") Integer heartbeatSkip,
    @JsonProperty("profile") Object profile,
    @JsonProperty("created-at") Instant createdAt,
    @JsonProperty("updated-at") Instant updatedAt,
    @JsonProperty("deleted-at") Instant deletedAt
)
{
    // The Prometheus password is intentionally never exposed in the REST representation.
    public static AssetInfo from(AssetEntity entity)
    {
        return switch (entity.properties)
        {
            case KubernetesAPIAsset kubernetes -> new AssetInfo(
                entity.id, entity.uuid, entity.name, entity.type,
                kubernetes.configPath(), kubernetes.namespace(), kubernetes.context().orElse(null), kubernetes.readTimeout().orElse(null),
                null, null, null, kubernetes.heartbeatSkip(),
                kubernetes.profile(),
                entity.createdAt, entity.updatedAt, entity.deletedAt
            );
            case LLMStaticAsset llm -> new AssetInfo(
                entity.id, entity.uuid, entity.name, entity.type,
                null, null, null, null,
                null, null, null, null,
                llm.profile(),
                entity.createdAt, entity.updatedAt, entity.deletedAt
            );
            case LLMPrometheusAsset prometheus -> new AssetInfo(
                entity.id, entity.uuid, entity.name, entity.type,
                null, null, null, null,
                prometheus.prometheusUrl(), prometheus.proxyUrl(), prometheus.username(), prometheus.heartbeatSkip(),
                prometheus.profile(),
                entity.createdAt, entity.updatedAt, entity.deletedAt
            );
            default -> throw new IllegalArgumentException("Unsupported asset type: " + entity.type);
        };
    }
}
