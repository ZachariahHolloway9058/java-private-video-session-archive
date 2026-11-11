package dev.infrai.mediaarchive.service;

import dev.infrai.mediaarchive.client.MediaGateway;
import dev.infrai.mediaarchive.config.ArchiveConfig;
import dev.infrai.mediaarchive.domain.RecordingUpload;
import dev.infrai.mediaarchive.domain.SessionHandoff;
import dev.infrai.mediaarchive.domain.SessionRequest;

public final class RecordingArchiveService {
    private static final int ROOM_CAPACITY = 12;
    private static final int EMPTY_ROOM_TIMEOUT_SECONDS = 300;
    private static final int PARTICIPANT_TOKEN_SECONDS = 3_600;
    private static final int UPLOAD_URL_SECONDS = 3_600;

    private final MediaGateway gateway;
    private final ArchiveConfig config;

    public RecordingArchiveService(MediaGateway gateway, ArchiveConfig config) {
        this.gateway = gateway;
        this.config = config;
    }

    public void prepareArchive() {
        gateway.createBucket(config.bucket());
    }

    public SessionHandoff openCreatorSession(SessionRequest request) {
        String room = "media-" + request.tenantId() + "-" + request.sessionId();
        String extension = request.contentType().equals("video/mp4") ? "mp4" : "webm";
        String objectKey = "tenants/" + request.tenantId() + "/creators/" + request.creatorId()
                + "/sessions/" + request.sessionId() + "/recording." + extension;
        String operationId = "recording-put-" + request.tenantId() + "-" + request.sessionId();

        gateway.createRoom(room, ROOM_CAPACITY, EMPTY_ROOM_TIMEOUT_SECONDS, config.region());
        String participantToken = gateway.issueRtcToken(
                room,
                request.creatorId(),
                request.displayName(),
                PARTICIPANT_TOKEN_SECONDS,
                true,
                true);
        RecordingUpload upload = gateway.presignRecordingPut(
                config.bucket(),
                objectKey,
                UPLOAD_URL_SECONDS,
                request.contentType(),
                request.maxRecordingBytes(),
                operationId);

        return new SessionHandoff(
                room,
                participantToken,
                config.bucket(),
                objectKey,
                upload.url(),
                upload.method(),
                upload.fields(),
                request.contentType(),
                request.maxRecordingBytes());
    }
}
