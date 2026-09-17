package tech.illuin.wombat.context.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tech.illuin.wombat.core.asset.Asset;

@Converter
@ApplicationScoped
public class AssetConverter implements AttributeConverter<Asset, String>
{
    @Inject JsonMapper mapper;

    @Override
    public String convertToDatabaseColumn(Asset attribute)
    {
        if (attribute == null)
            return null;
        try
        {
            return this.mapper.writeValueAsString(attribute);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize asset data", e);
        }
    }

    @Override
    public Asset convertToEntityAttribute(String dbData)
    {
        if (dbData == null || dbData.isBlank())
            return null;
        try
        {
            return this.mapper.readValue(dbData, Asset.class);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize asset data: " + dbData, e);
        }
    }
}
