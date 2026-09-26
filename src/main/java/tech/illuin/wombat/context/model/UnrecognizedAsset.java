package tech.illuin.wombat.context.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotBlank;
import tech.illuin.wombat.core.asset.ActivityRegime;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetType;
import tech.illuin.wombat.core.asset.ServiceFamily;
import tech.illuin.wombat.core.asset.profile.Profile;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnrecognizedAsset(
    @NotBlank @JsonProperty("id") String id,
    @NotBlank @JsonProperty("environment-id") String environmentId,
    @NotBlank @JsonProperty("name") String name,
    @JsonProperty("type") String rawType,
    @JsonIgnore String rawJson
) implements Asset
{
    private static final AssetType UNKNOWN_TYPE = AssetType.of(
        "tech.illuin",
        "wombat-core",
        "unknown",
        ActivityRegime.UNKNOWN,
        ServiceFamily.UNKNOWN
    );

    private static final Profile UNKNOWN_PROFILE = () -> "unknown";

    public UnrecognizedAsset
    {
        if (rawType == null)
            rawType = UnrecognizedAssetProblemHandler.getCurrentUnknownType();
    }

    @Override
    public AssetType type()
    {
        return UNKNOWN_TYPE;
    }

    @Override
    public Profile profile()
    {
        return UNKNOWN_PROFILE;
    }
}
