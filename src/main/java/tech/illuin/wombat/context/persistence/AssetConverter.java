package tech.illuin.wombat.context.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tech.illuin.wombat.context.model.UnrecognizedAsset;
import tech.illuin.wombat.core.asset.Asset;

@Converter
@ApplicationScoped
public class AssetConverter implements AttributeConverter<Asset, String>
{
    @Inject JsonMapper mapper;

    public AssetConverter() {}

    public AssetConverter(JsonMapper mapper)
    {
        this.mapper = mapper;
    }

    @Override
    public String convertToDatabaseColumn(Asset attribute)
    {
        try {
            if (attribute == null)
                return null;
            if (attribute instanceof UnrecognizedAsset unrecognized && unrecognized.rawJson() != null)
                return unrecognized.rawJson();
            return this.mapper.writeValueAsString(attribute);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize asset data", e);
        }
    }

    @Override
    public Asset convertToEntityAttribute(String dbData)
    {
        try {
            if (dbData == null || dbData.isBlank())
                return null;
            Asset asset = this.mapper.readValue(dbData, Asset.class);
            if (asset instanceof UnrecognizedAsset unrecognized && unrecognized.rawJson() == null)
                return new UnrecognizedAsset(unrecognized.identity(), unrecognized.rawType(), dbData);
            return asset;
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize asset data: " + dbData, e);
        }
    }
}
