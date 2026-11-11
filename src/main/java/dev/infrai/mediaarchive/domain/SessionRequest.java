package dev.infrai.mediaarchive.domain;

import dev.infrai.mediaarchive.client.Json;

import java.util.Map;

public record SessionRequest(
        String tenantId,
        String creatorId,
        String sessionId,
        String displayName,
        String contentType,
        long maxRecordingBytes) {

    public SessionRequest {
        tenantId = identifier("tenantId", tenantId);
        creatorId = identifier("creatorId", creatorId);
        sessionId = identifier("sessionId", sessionId);
        displayName = nonBlank("displayName", displayName);
        contentType = nonBlank("contentType", contentType);
        if (!contentType.equals("video/mp4") && !contentType.equals("video/webm")) {
            throw new IllegalArgumentException("contentType must be video/mp4 or video/webm");
        }
        if (maxRecordingBytes < 1 || maxRecordingBytes > 10_737_418_240L) {
            throw new IllegalArgumentException("maxRecordingBytes must be between 1 and 10737418240");
        }
    }

    public static SessionRequest fromJson(String json) {
        Map<String, Object> input = Json.object(Json.parse(json));
        return new SessionRequest(
                text(input, "tenantId"),
                text(input, "creatorId"),
                text(input, "sessionId"),
                text(input, "displayName"),
                text(input, "contentType"),
                number(input, "maxRecordingBytes"));
    }

    private static String text(Map<String, Object> input, String field) {
        Object value = input.get(field);
        if (!(value instanceof String text)) throw new IllegalArgumentException(field + " must be a string");
        return text;
    }

    private static long number(Map<String, Object> input, String field) {
        Object value = input.get(field);
        if (!(value instanceof Number number)) throw new IllegalArgumentException(field + " must be a number");
        return number.longValue();
    }

    private static String identifier(String field, String value) {
        nonBlank(field, value);
        if (!value.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException(field + " must use letters, digits, underscore, or hyphen");
        }
        return value;
    }

    private static String nonBlank(String field, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
