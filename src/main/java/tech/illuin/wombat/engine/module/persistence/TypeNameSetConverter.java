package tech.illuin.wombat.engine.module.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Stores asset-type names comma-separated, sorted; a name holds no comma.
 */
@Converter
public class TypeNameSetConverter implements AttributeConverter<Set<String>, String>
{
    @Override
    public String convertToDatabaseColumn(Set<String> names)
    {
        return names.stream()
            .sorted()
            .collect(Collectors.joining(","));
    }

    @Override
    public Set<String> convertToEntityAttribute(String column)
    {
        if (column == null || column.isBlank())
            return new TreeSet<>();
        return Arrays.stream(column.split(","))
            .collect(Collectors.toCollection(TreeSet::new));
    }
}
