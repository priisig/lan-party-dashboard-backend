package com.lanparty.dashboard.common;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Small helpers for the JSON blobs we keep in text columns (integration config, cached results). */
public final class Json {

    public static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {
    }

    public static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return MAPPER.createObjectNode();
        }
        return MAPPER.readTree(json);
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    public static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || value.asString().isBlank() ? fallback : value.asString();
    }

    public static int integer(JsonNode node, String field, int fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? fallback : value.asInt(fallback);
    }
}
