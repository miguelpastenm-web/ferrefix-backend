package cl.ferrefix.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** boolean <-> NUMBER(1,0). Evita que Hibernate espere un BOOLEAN nativo de Oracle 23 al validar el esquema. */
@Converter
public class BooleanNumberConverter implements AttributeConverter<Boolean, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    @Override
    public Boolean convertToEntityAttribute(Integer value) {
        return value != null && value == 1;
    }
}
