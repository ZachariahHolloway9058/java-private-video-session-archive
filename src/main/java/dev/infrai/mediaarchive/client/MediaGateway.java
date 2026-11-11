package dev.infrai.mediaarchive.client;

import dev.infrai.mediaarchive.domain.RecordingUpload;

public interface MediaGateway {
    void createBucket(String name);
    void createRoom(String name, int maxParticipants, int emptyTimeoutSeconds, String region);
    String issueRtcToken(String room, String identity, String displayName, int ttlSeconds,
                         boolean canPublish, boolean canSubscribe);
    RecordingUpload presignRecordingPut(String bucket, String objectKey, int expiresSeconds,
                               String contentType, long maxBytes, String idempotencyKey);
}
