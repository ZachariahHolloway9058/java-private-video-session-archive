package dev.infrai.mediaarchive;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.infrai.mediaarchive.client.InfraiClient;
import dev.infrai.mediaarchive.client.InfraiException;
import dev.infrai.mediaarchive.client.Json;
import dev.infrai.mediaarchive.config.ArchiveConfig;
import dev.infrai.mediaarchive.domain.SessionHandoff;
import dev.infrai.mediaarchive.domain.SessionRequest;
import dev.infrai.mediaarchive.service.RecordingArchiveService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

public final class VideoSessionArchive {
    private VideoSessionArchive() {}

    public static void main(String[] args) throws IOException {
        ArchiveConfig config = ArchiveConfig.fromEnvironment();
        RecordingArchiveService service = new RecordingArchiveService(new InfraiClient(config), config);
        service.prepareArchive();

        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/sessions", exchange -> openSession(exchange, service));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("Media archive service listening on http://localhost:" + config.port());
    }

    private static void openSession(HttpExchange exchange, RecordingArchiveService service) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, Json.write(Map.of("error", "method_not_allowed")));
            return;
        }
        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            SessionHandoff handoff = service.openCreatorSession(SessionRequest.fromJson(body));
            send(exchange, 201, handoff.toJson());
        } catch (InfraiException error) {
            int status = error.statusCode() >= 400 && error.statusCode() < 500 ? error.statusCode() : 502;
            send(exchange, status, Json.write(Map.of("error", error.code(), "detail", error.detail())));
        } catch (IllegalArgumentException error) {
            send(exchange, 400, Json.write(Map.of("error", "invalid_request", "message", error.getMessage())));
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
