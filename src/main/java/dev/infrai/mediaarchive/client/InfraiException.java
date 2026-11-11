package dev.infrai.mediaarchive.client;

import java.util.Map;

public final class InfraiException extends RuntimeException {
    private final String code;
    private final int statusCode;
    private final Map<String, Object> detail;

    public InfraiException(String code, int statusCode, Map<String, Object> detail) {
        super(code + ": " + detail.getOrDefault("message", "request rejected"));
        this.code = code;
        this.statusCode = statusCode;
        this.detail = Map.copyOf(detail);
    }

    public String code() { return code; }
    public int statusCode() { return statusCode; }
    public Map<String, Object> detail() { return detail; }
}
