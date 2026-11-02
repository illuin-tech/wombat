package tech.illuin.wombat.security.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tech.illuin.wombat.security.Role;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class RoleSetConverter implements AttributeConverter<Set<Role>, String>
{
    @Override
    public String convertToDatabaseColumn(Set<Role> roles)
    {
        return roles.stream()
            .map(Role::value)
            .sorted()
            .collect(Collectors.joining(","));
    }

    @Override
    public Set<Role> convertToEntityAttribute(String column)
    {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        if (column == null || column.isBlank())
            return roles;

        Arrays.stream(column.split(","))
            .map(Role::fromValue)
            .flatMap(Optional::stream)
            .forEach(roles::add);
        return roles;
    }
}
