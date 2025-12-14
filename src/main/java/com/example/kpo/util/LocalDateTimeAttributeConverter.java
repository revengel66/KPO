package com.example.kpo.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Converter(autoApply = false)
public class LocalDateTimeAttributeConverter implements AttributeConverter<LocalDateTime, String> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Override
    public String convertToDatabaseColumn(LocalDateTime attribute) {
        if (attribute == null) {
            return null;
        }
        return FORMATTER.format(attribute);
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        String value = dbData.trim();
        if (value.chars().allMatch(Character::isDigit)) {
            long epoch = Long.parseLong(value);
            if (value.length() <= 10) {
                return LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZONE);
            }
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZONE);
        }
        value = value.replace('T', ' ').replace('/', '-');
        if (value.contains(".")) {
            value = value.substring(0, value.indexOf('.'));
        }
        if (value.length() == 10) {
            value = value + " 00:00:00";
        }
        try {
            return LocalDateTime.parse(value, FORMATTER);
        } catch (DateTimeParseException first) {
            try {
                return LocalDateTime.parse(value.replace(' ', 'T'));
            } catch (DateTimeParseException ignored) {
                Timestamp timestamp = Timestamp.valueOf(value);
                return timestamp.toInstant().atZone(ZONE).toLocalDateTime();
            }
        }
    }
}
