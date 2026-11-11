package dev.infrai.mediaarchive.domain;

import dev.infrai.mediaarchive.client.Json;

import java.util.LinkedHashMap;
import java.util.Map;

public record SessionHandoff(
        String room,
        String participantToken,
        String archiveBucket,
        String archiveObjectKey,
        String recordingUploadUrl,
        String recordingUploadMethod,
        Map<String, Object> recordingUploadFields,
        String recordingContentType,
        long recordingMaxBytes) {

    public String toJson() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("room", room);
        body.put("participantToken", participantToken);
        body.put("archiveBucket", archiveBucket);
        body.put("archiveObjectKey", archiveObjectKey);
        body.put("recordingUploadUrl", recordingUploadUrl);
        body.put("recordingUploadMethod", recordingUploadMethod);
        body.put("recordingUploadFields", recordingUploadFields);
        body.put("recordingContentType", recordingContentType);
        body.put("recordingMaxBytes", recordingMaxBytes);
        return Json.write(body);
    }
}
