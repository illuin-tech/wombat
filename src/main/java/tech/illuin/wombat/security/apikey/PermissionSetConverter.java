package tech.illuin.wombat.security.apikey;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tech.illuin.wombat.security.Permission;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class PermissionSetConverter implements AttributeConverter<Set<Permission>, String>
{
    @Override
    public String convertToDatabaseColumn(Set<Permission> permissions)
    {
        return permissions.stream()
            .map(Permission::value)
            .sorted()
            .collect(Collectors.joining(","));
    }

    @Override
    public Set<Permission> convertToEntityAttribute(String column)
    {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        if (column == null || column.isBlank())
            return permissions;

        Arrays.stream(column.split(","))
            .map(Permission::fromValue)
            .flatMap(Optional::stream)
            .forEach(permissions::add);
        return permissions;
    }
}
