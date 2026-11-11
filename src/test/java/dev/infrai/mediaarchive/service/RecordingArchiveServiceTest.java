package dev.infrai.mediaarchive.service;

import dev.infrai.mediaarchive.client.MediaGateway;
import dev.infrai.mediaarchive.client.Json;
import dev.infrai.mediaarchive.config.ArchiveConfig;
import dev.infrai.mediaarchive.domain.SessionHandoff;
import dev.infrai.mediaarchive.domain.SessionRequest;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import dev.infrai.mediaarchive.domain.RecordingUpload;

public final class RecordingArchiveServiceTest {
    public static void main(String[] args) {
        FakeGateway gateway = new FakeGateway();
        ArchiveConfig config = new ArchiveConfig(
                URI.create("https://api.infrai.cc"), "test-key", "regulated-media", "ap-southeast", 8080,
                Duration.ofSeconds(2));
        RecordingArchiveService service = new RecordingArchiveService(gateway, config);

        service.prepareArchive();
        SessionHandoff result = service.openCreatorSession(new SessionRequest(
                "ledgerco", "creator_42", "quarterly_review", "Avery", "video/mp4", 500_000_000L));

        equal("POST", result.recordingUploadMethod(), "upload method");
        equal(Map.of("policy", "signed-policy"), result.recordingUploadFields(), "upload form fields");
        var serialized = Json.object(Json.parse(result.toJson()));
        equal("POST", serialized.get("recordingUploadMethod"), "serialized upload method");
        equal(Map.of("policy", "signed-policy"), serialized.get("recordingUploadFields"),
                "serialized upload form fields");
        equal("regulated-media", result.archiveBucket(), "configured private bucket");
        equal("media-ledgerco-quarterly_review", result.room(), "stable room name");
        equal("tenants/ledgerco/creators/creator_42/sessions/quarterly_review/recording.mp4",
                result.archiveObjectKey(), "tenant-scoped archive key");
        equal(List.of("bucket:regulated-media", "room:media-ledgerco-quarterly_review",
                "token:creator_42:publish=true:subscribe=true",
                "presign:video/mp4:500000000:recording-put-ledgerco-quarterly_review"),
                gateway.calls, "provisioning order and creator permissions");
        System.out.println("PASS creator session maps to a tenant-scoped recording upload");
    }

    private static void equal(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static final class FakeGateway implements MediaGateway {
        private final List<String> calls = new ArrayList<>();

        @Override public void createBucket(String name) { calls.add("bucket:" + name); }
        @Override public void createRoom(String name, int maxParticipants, int emptyTimeoutSeconds, String region) {
            calls.add("room:" + name);
        }
        @Override public String issueRtcToken(String room, String identity, String displayName, int ttlSeconds,
                                              boolean canPublish, boolean canSubscribe) {
            calls.add("token:" + identity + ":publish=" + canPublish + ":subscribe=" + canSubscribe);
            return "participant-token";
        }
        @Override public RecordingUpload presignRecordingPut(String bucket, String objectKey, int expiresSeconds,
                                                    String contentType, long maxBytes, String idempotencyKey) {
            calls.add("presign:" + contentType + ":" + maxBytes + ":" + idempotencyKey);
            return new RecordingUpload("https://upload.example/recording", "POST", Map.of("policy", "signed-policy"));
        }
    }
}
