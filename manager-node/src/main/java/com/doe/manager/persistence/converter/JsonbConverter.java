package com.doe.manager.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;

import java.sql.SQLException;

/**
 * JPA {@link AttributeConverter} that maps a Java {@link String} to a PostgreSQL
 * {@code JSONB} column.
 *
 * <p>Without this converter, the JDBC driver sends the string as {@code text} /
 * {@code character varying}, causing a PostgreSQL type-mismatch error:
 * <em>"column is of type jsonb but expression is of type character varying"</em>.
 * Wrapping the value in a {@link PGobject} with type {@code "jsonb"} makes the
 * driver include the correct OID in the protocol packet.
 */
@Converter
public class JsonbConverter implements AttributeConverter<String, Object> {

    @Override
    public Object convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            PGobject pgo = new PGobject();
            pgo.setType("jsonb");
            pgo.setValue(attribute);
            return pgo;
        } catch (SQLException e) {
            throw new IllegalArgumentException("Failed to convert String to JSONB PGobject", e);
        }
    }

    @Override
    public String convertToEntityAttribute(Object dbData) {
        if (dbData == null) {
            return null;
        }
        if (dbData instanceof PGobject pgo) {
            return pgo.getValue();
        }
        return dbData.toString();
    }
}
