package dev.infrai.mediaarchive.client;

import dev.infrai.mediaarchive.config.ArchiveConfig;
import dev.infrai.mediaarchive.domain.RecordingUpload;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InfraiClient implements MediaGateway {
    private static final int MAX_ATTEMPTS = 4;
    private final HttpClient http;
    private final ArchiveConfig config;

    public InfraiClient(ArchiveConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build());
    }

    InfraiClient(ArchiveConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public void createBucket(String name) {
        post("/v1/storage/bucket/create", Map.of("name", name));
    }

    @Override
    public void createRoom(String name, int maxParticipants, int emptyTimeoutSeconds, String region) {
        post("/v1/rtc/room/create", Map.of(
                "name", name,
                "max_participants", maxParticipants,
                "empty_timeout_s", emptyTimeoutSeconds,
                "region", region));
    }

    @Override
    public String issueRtcToken(String room, String identity, String displayName, int ttlSeconds,
                                boolean canPublish, boolean canSubscribe) {
        Map<String, Object> data = post("/v1/rtc/token/issue", Map.of(
                "room", room,
                "identity", identity,
                "display_name", displayName,
                "ttl_s", ttlSeconds,
                "can_publish", canPublish,
                "can_subscribe", canSubscribe));
        return requiredString(data, "token");
    }

    @Override
    public RecordingUpload presignRecordingPut(String bucket, String objectKey, int expiresSeconds,
                                      String contentType, long maxBytes, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("op", "put");
        body.put("expires_seconds", expiresSeconds);
        body.put("content_type", contentType);
        body.put("max_bytes", maxBytes);
        body.put("idempotency_key", idempotencyKey);
        Map<String, Object> data = post("/v1/storage/object/presign/" + segment(bucket) + "/" + segment(objectKey), body);
        return new RecordingUpload(requiredString(data, "url"), requiredString(data, "method"),
                data.get("fields") instanceof Map<?, ?> fields ? Json.object(fields) : Map.of());
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        String json = Json.write(body);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUri().resolve(path))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .method("POST", HttpRequest.BodyPublishers.ofString(json))
                    .build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                Map<String, Object> envelope = decodeEnvelope(response.body(), response.statusCode());
                if (response.statusCode() == 429 && attempt + 1 < MAX_ATTEMPTS) {
                    sleep(retryDelay(response, attempt));
                    continue;
                }
                return unwrap(envelope, response.statusCode());
            } catch (IOException error) {
                throw new IllegalStateException("Infrai transport failed", error);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Infrai request interrupted", error);
            }
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private static Map<String, Object> decodeEnvelope(String body, int statusCode) {
        try {
            return Json.object(Json.parse(body));
        } catch (RuntimeException error) {
            throw new IllegalStateException("Infrai returned an invalid envelope with HTTP " + statusCode, error);
        }
    }

    private static Map<String, Object> unwrap(Map<String, Object> envelope, int statusCode) {
        if (!Boolean.TRUE.equals(envelope.get("ok"))) {
            Map<String, Object> detail = envelope.get("error") instanceof Map<?, ?> value
                    ? Json.object(value) : Map.of("message", "request rejected");
            String code = String.valueOf(detail.getOrDefault("code", "INFRAI_REQUEST_REJECTED"));
            throw new InfraiException(code, statusCode, detail);
        }
        Object data = envelope.get("data");
        return data instanceof Map<?, ?> value ? Json.object(value) : Map.of();
    }

    private static String requiredString(Map<String, Object> data, String field) {
        Object value = data.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Infrai response is missing data." + field);
        }
        return text;
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("").trim();
        if (!value.isEmpty()) {
            try { return Duration.ofSeconds(Math.max(1, Long.parseLong(value))); }
            catch (NumberFormatException ignored) {
                try {
                    long millis = Duration.between(ZonedDateTime.now(),
                            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)).toMillis();
                    return Duration.ofMillis(Math.max(1_000, millis));
                } catch (DateTimeParseException ignoredDate) { }
            }
        }
        return Duration.ofMillis(250L * (1L << attempt));
    }

    private static void sleep(Duration delay) throws InterruptedException {
        Thread.sleep(delay.toMillis());
    }

    private static String segment(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            int current = item & 0xff;
            if ((current >= 'a' && current <= 'z') || (current >= 'A' && current <= 'Z')
                    || (current >= '0' && current <= '9') || "-._~".indexOf(current) >= 0) {
                encoded.append((char) current);
            } else {
                encoded.append('%').append(String.format("%02X", current));
            }
        }
        return encoded.toString();
    }
}
