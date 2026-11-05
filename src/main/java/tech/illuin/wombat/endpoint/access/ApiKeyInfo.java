package tech.illuin.wombat.endpoint.access;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tech.illuin.wombat.security.Permission;
import tech.illuin.wombat.security.apikey.ApiKeyEntity;
import tech.illuin.wombat.security.apikey.ApiKeyService;

import java.time.Instant;
import java.util.List;

public record ApiKeyInfo(
    @JsonProperty("id") String id,
    @JsonProperty("name") String name,
    @JsonProperty("permissions") List<String> permissions,
    @JsonProperty("owner") String owner,
    @JsonProperty("created-at") Instant createdAt,
    @JsonProperty("expires-at") Instant expiresAt,
    @JsonProperty("revoked-at") Instant revokedAt,
    @JsonProperty("last-used-at") Instant lastUsedAt,
    @JsonProperty("token") @JsonInclude(JsonInclude.Include.NON_NULL) String token
) {
    public static ApiKeyInfo from(ApiKeyService.Listed listed)
    {
        return from(listed.key(), listed.owner(), null);
    }

    public static ApiKeyInfo from(ApiKeyEntity key, String owner, String token)
    {
        return new ApiKeyInfo(
            key.id, key.name,
            key.permissions.stream().map(Permission::value).sorted().toList(),
            owner,
            key.createdAt, key.expiresAt, key.revokedAt, key.lastUsedAt,
            token
        );
    }
}
