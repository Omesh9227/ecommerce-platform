package com.ecommerce.apigateway.support;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A fake downstream service (stands in for user-service) built on the JDK's HTTP server.
 * It echoes back what the gateway forwarded, as JSON, so tests can assert on it:
 * { "path", "method", "userId", "userEmail", "role", "correlationId", "authorizationPresent" }
 * Fields whose header was not forwarded are omitted.
 */
public final class StubDownstreamServer {

    private StubDownstreamServer() {
    }

    public static HttpServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            Headers headers = exchange.getRequestHeaders();

            List<String> fields = new ArrayList<>();
            addField(fields, "path", exchange.getRequestURI().getPath());
            addField(fields, "method", exchange.getRequestMethod());
            addField(fields, "userId", headers.getFirst("X-User-Id"));
            addField(fields, "userEmail", headers.getFirst("X-User-Email"));
            addField(fields, "role", headers.getFirst("X-User-Role"));
            addField(fields, "correlationId", headers.getFirst("X-Correlation-Id"));
            fields.add("\"authorizationPresent\":" + (headers.getFirst("Authorization") != null));

            byte[] body = ("{" + String.join(",", fields) + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return server;
    }

    public static String baseUrl(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static void addField(List<String> fields, String name, String value) {
        if (value != null) {
            String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
            fields.add("\"" + name + "\":\"" + escaped + "\"");
        }
    }
}
