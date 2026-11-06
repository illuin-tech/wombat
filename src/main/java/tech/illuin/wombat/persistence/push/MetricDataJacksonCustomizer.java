package tech.illuin.wombat.persistence.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;
import tech.illuin.wombat.core.source.data.MetricData;

@Singleton
public class MetricDataJacksonCustomizer implements ObjectMapperCustomizer
{
    @Override
    public void customize(ObjectMapper mapper)
    {
        register(mapper);
    }

    public static ObjectMapper register(ObjectMapper mapper)
    {
        return mapper.addMixIn(MetricData.class, MetricDataMixin.class);
    }
}
