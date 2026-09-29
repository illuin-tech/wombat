package tech.illuin.wombat.context.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.quarkus.runtime.annotations.RegisterForReflection;
import tech.illuin.wombat.core.asset.type.ActivityRegime;
import tech.illuin.wombat.core.asset.Asset;
import tech.illuin.wombat.core.asset.AssetIdentity;
import tech.illuin.wombat.core.asset.type.AssetType;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.asset.profile.AssetProfile;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnrecognizedAsset(
    @JsonUnwrapped AssetIdentity identity,
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

    private static final AssetProfile UNKNOWN_PROFILE = () -> "unknown";

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
    public AssetProfile profile()
    {
        return UNKNOWN_PROFILE;
    }
}
