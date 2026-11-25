package tech.illuin.wombat.engine.module.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Stores asset-type names comma-separated, in their order; a name holds no comma.
 */
@Converter
public class TypeNameListConverter implements AttributeConverter<List<String>, String>
{
    @Override
    public String convertToDatabaseColumn(List<String> names)
    {
        return String.join(",", names);
    }

    @Override
    public List<String> convertToEntityAttribute(String column)
    {
        if (column == null || column.isBlank())
            return new ArrayList<>();
        return Arrays.stream(column.split(","))
            .collect(Collectors.toCollection(ArrayList::new));
    }
}
