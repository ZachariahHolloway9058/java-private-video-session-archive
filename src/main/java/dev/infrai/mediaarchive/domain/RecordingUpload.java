package dev.infrai.mediaarchive.domain;

import java.util.Map;

public record RecordingUpload(String url, String method, Map<String, Object> fields) {
    public RecordingUpload {
        fields = Map.copyOf(fields);
    }
}
