package tech.illuin.wombat.persistence.push;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tech.illuin.wombat.core.asset.type.ServiceFamily;
import tech.illuin.wombat.core.source.data.KubernetesData;
import tech.illuin.wombat.core.source.data.LLMData;

/**
 * Makes {@link tech.illuin.wombat.core.source.data.MetricData} polymorphic in JSON: its {@link ServiceFamily} is
 * written to, and read from, a {@code family} property.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "family")
@JsonSubTypes({
    @JsonSubTypes.Type(value = KubernetesData.class, name = "KUBERNETES_CONTAINER"),
    @JsonSubTypes.Type(value = LLMData.class, name = "LLM"),
})
public abstract class MetricDataMixin
{
    @JsonIgnore
    public abstract ServiceFamily serviceFamily();
}
