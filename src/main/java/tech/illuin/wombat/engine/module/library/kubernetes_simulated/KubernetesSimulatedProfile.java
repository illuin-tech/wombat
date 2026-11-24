package tech.illuin.wombat.engine.module.library.kubernetes_simulated;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import tech.illuin.wombat.core.activity.commons.Country;
import tech.illuin.wombat.core.asset.profile.AssetProfile;
import tech.illuin.wombat.core.asset.profile.Lifetime;

// Profiles without a type are cloud ones, as all were before bare servers were supported
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type", defaultImpl = KubernetesSimulatedCloudProfile.class)
@JsonSubTypes({
    @JsonSubTypes.Type(value = KubernetesSimulatedCloudProfile.class, name = "cloud"),
    @JsonSubTypes.Type(value = KubernetesSimulatedBareServerProfile.class, name = "server")
})
@Schema(
    discriminatorProperty = "type",
    discriminatorMapping = {
        @DiscriminatorMapping(value = "cloud", schema = KubernetesSimulatedCloudProfile.class),
        @DiscriminatorMapping(value = "server", schema = KubernetesSimulatedBareServerProfile.class)
    },
    oneOf = { KubernetesSimulatedCloudProfile.class, KubernetesSimulatedBareServerProfile.class }
)
public sealed interface KubernetesSimulatedProfile extends AssetProfile permits KubernetesSimulatedCloudProfile, KubernetesSimulatedBareServerProfile
{
    Country location();

    Lifetime lifespan();

    int nodeCount();
}
