package com.burpext.sessionx.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;

/**
 * Singleton Jackson ObjectMapper with java.time support pre-configured.
 */
public class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonUtil() {}

    public static String toJson(Object obj) throws Exception {
        return MAPPER.writeValueAsString(obj);
    }

    public static String toPrettyJson(Object obj) throws Exception {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    }

    public static <T> T fromJson(String json, Class<T> cls) throws Exception {
        return MAPPER.readValue(json, cls);
    }

    public static <T> List<T> fromJsonList(String json, Class<T> elementClass) throws Exception {
        return MAPPER.readValue(json,
            MAPPER.getTypeFactory().constructCollectionType(List.class, elementClass));
    }
}
