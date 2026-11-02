package tech.illuin.wombat.endpoint.access;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record ApiKeyRequest(
    @JsonProperty("name") String name,
    @JsonProperty("permissions") List<String> permissions,
    @JsonProperty("expires-at") Instant expiresAt
) {}
