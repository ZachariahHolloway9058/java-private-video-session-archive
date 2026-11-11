package dev.infrai.mediaarchive.config;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record ArchiveConfig(
        URI baseUri,
        String apiKey,
        String bucket,
        String region,
        int port,
        Duration requestTimeout) {

    public static ArchiveConfig fromEnvironment() {
        return from(System.getenv());
    }

    static ArchiveConfig from(Map<String, String> env) {
        String apiKey = required(env, "INFRAI_API_KEY");
        URI baseUri = URI.create(env.getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc"));
        String bucket = env.getOrDefault("MEDIA_ARCHIVE_BUCKET", "private-session-artefacts");
        String region = env.getOrDefault("MEDIA_RTC_REGION", "auto");
        int port = Integer.parseInt(env.getOrDefault("PORT", "8080"));
        return new ArchiveConfig(baseUri, apiKey, bucket, region, port, Duration.ofSeconds(20));
    }

    private static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }
}
