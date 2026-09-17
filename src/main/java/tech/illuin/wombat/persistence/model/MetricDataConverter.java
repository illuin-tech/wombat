package tech.illuin.wombat.persistence.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import tech.illuin.wombat.core.source.data.MetricData;

/**
 * Base for the metric-data converters.
 * <p>
 * Each metric table is homogeneous — {@code server_metrics} only ever holds Kubernetes samples, {@code model_metrics}
 * only LLM ones — so subclasses bind a concrete {@link MetricData} type instead of making the column polymorphic.
 * That keeps the stored JSON free of a type discriminator on what are by far the highest-volume tables.
 */
public abstract class MetricDataConverter<T extends MetricData> implements AttributeConverter<T, String>
{
    private final Class<T> type;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected MetricDataConverter(Class<T> type)
    {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(T attribute)
    {
        if (attribute == null)
            return null;
        try
        {
            return MAPPER.writeValueAsString(attribute);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize metric data", e);
        }
    }

    @Override
    public T convertToEntityAttribute(String dbData)
    {
        if (dbData == null || dbData.isBlank())
            return null;
        try
        {
            return MAPPER.readValue(dbData, this.type);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize metric data: " + dbData, e);
        }
    }
}
