package tech.illuin.wombat.persistence.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores the compaction flag as 0/1. SQLite has no boolean type, and letting each dialect pick its
 * own would land the two migration chains on column types that are not equivalent.
 */
@Converter
public class CompactionFlagConverter implements AttributeConverter<Boolean, Long>
{
    @Override
    public Long convertToDatabaseColumn(Boolean attribute)
    {
        return Boolean.TRUE.equals(attribute) ? 1L : 0L;
    }

    @Override
    public Boolean convertToEntityAttribute(Long dbData)
    {
        return dbData != null && dbData != 0L;
    }
}
