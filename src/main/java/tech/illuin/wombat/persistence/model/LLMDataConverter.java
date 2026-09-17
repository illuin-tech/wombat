package tech.illuin.wombat.persistence.model;

import jakarta.persistence.Converter;
import tech.illuin.wombat.core.source.data.LLMData;

@Converter
public class LLMDataConverter extends MetricDataConverter<LLMData>
{
    public LLMDataConverter()
    {
        super(LLMData.class);
    }
}
