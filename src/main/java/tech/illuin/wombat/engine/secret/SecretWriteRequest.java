package tech.illuin.wombat.engine.secret;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record SecretWriteRequest(
    @NotBlank @JsonProperty("key") @Schema(required = true) String key,
    @NotBlank @JsonProperty("value") @Schema(required = true, writeOnly = true) String value
) {
    @Override
    public String toString()
    {
        return "SecretWriteRequest[redacted]";
    }
}
