package tech.illuin.wombat.endpoint.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;
import tech.illuin.wombat.core.evaluation.AssetEvaluation;
import tech.illuin.wombat.core.evaluation.AssetEvaluation.EvaluationType;

/**
 * Serves the {@code type} of every {@link AssetEvaluation}, the discriminator its OpenAPI schema declares
 * ({@code AssetEvaluationSchema}). The core's records implement {@code type()} without declaring it as a component, so
 * Jackson would leave it out.
 */
@Singleton
public class AssetEvaluationTypeCustomizer implements ObjectMapperCustomizer
{
    @Override
    public void customize(ObjectMapper mapper)
    {
        mapper.addMixIn(AssetEvaluation.class, AssetEvaluationMixin.class);
    }

    private interface AssetEvaluationMixin
    {
        /* Read only: it follows from the record's class, so it is ignored when one is read */
        @JsonProperty(value = "type", access = JsonProperty.Access.READ_ONLY)
        EvaluationType type();
    }
}
