package tech.illuin.wombat.engine.module.persistence;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypeNameListConverterTest
{
    private final TypeNameListConverter converter = new TypeNameListConverter();

    @Test
    void theNamesKeepTheirOrder()
    {
        String column = this.converter.convertToDatabaseColumn(List.of("c.d", "a.b"));

        assertEquals("c.d,a.b", column);
        assertEquals(List.of("c.d", "a.b"), this.converter.convertToEntityAttribute(column));
    }

    @Test
    void aBlankColumnIsNoName()
    {
        assertEquals(List.of(), this.converter.convertToEntityAttribute(""));
        assertEquals(List.of(), this.converter.convertToEntityAttribute(null));
    }
}
