package com.stabdan.cardcatalog.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Json() {}

    public static String stringify(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Unable to serialize JSON", e);
        }
    }

    public static <T> T parse(String body, Class<T> type) {
        try {
            return MAPPER.readValue(body == null || body.isBlank() ? "{}" : body, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON body: " + e.getMessage(), e);
        }
    }
}
