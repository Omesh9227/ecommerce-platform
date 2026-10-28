package com.ecommerce.reviewservice.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Stands in for product-service (GET /api/v1/products/lookup - only products for sale) and user-service
 * (GET /api/v1/users/me - the caller's name) in tests.
 */
public final class StubServices {

    public enum Service { PRODUCT, USER }

    private final HttpServer server;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<UUID, Map<String, Object>> products = new ConcurrentHashMap<>();
    private final Map<String, String[]> namesByAuthorization = new ConcurrentHashMap<>();
    private final Map<Service, Boolean> down = new ConcurrentHashMap<>();

    private StubServices(HttpServer server) {
        this.server = server;
    }

    public static StubServices start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newFixedThreadPool(16));
            StubServices stub = new StubServices(server);
            server.createContext("/api/v1/products/lookup", stub::handleLookup);
            server.createContext("/api/v1/users/me", stub::handleMe);
            server.start();
            return stub;
        } catch (IOException e) {
            throw new IllegalStateException("Could not start the stub server", e);
        }
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void reset() {
        products.clear();
        namesByAuthorization.clear();
        down.clear();
    }

    /** A product for sale. */
    public UUID product(String sku) {
        UUID id = UUID.randomUUID();
        Map<String, Object> p = new ConcurrentHashMap<>();
        p.put("id", id.toString());
        p.put("sku", sku);
        p.put("name", "Product " + sku);
        p.put("price", new BigDecimal("1000"));
        p.put("sellingPrice", new BigDecimal("1000"));
        p.put("currency", "INR");
        p.put("status", "ACTIVE");
        p.put("availableQuantity", 5);
        p.put("inStock", true);
        p.put("imageUrls", List.of());
        products.put(id, p);
        return id;
    }

    /** The name user-service returns for this Authorization header (default: Omesh Kumar). */
    public void name(String authorization, String firstName, String lastName) {
        namesByAuthorization.put(authorization, new String[]{firstName, lastName});
    }

    public void setDown(Service service, boolean isDown) {
        down.put(service, isDown);
    }

    private void handleLookup(HttpExchange exchange) throws IOException {
        if (Boolean.TRUE.equals(down.get(Service.PRODUCT))) {
            respond(exchange, 503, Map.of("status", 503, "message", "down"));
            return;
        }
        String query = exchange.getRequestURI().getRawQuery();
        List<Object> found = new ArrayList<>();
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("ids=")) {
                    for (String id : URLDecoder.decode(param.substring(4), StandardCharsets.UTF_8).split(",")) {
                        Map<String, Object> p = products.get(UUID.fromString(id.trim()));
                        if (p != null) {
                            found.add(new LinkedHashMap<>(p));
                        }
                    }
                }
            }
        }
        respond(exchange, 200, Map.of("success", true, "message", "Products fetched successfully", "data", found));
    }

    private void handleMe(HttpExchange exchange) throws IOException {
        if (Boolean.TRUE.equals(down.get(Service.USER))) {
            respond(exchange, 503, Map.of("status", 503, "message", "down"));
            return;
        }
        String[] name = namesByAuthorization.getOrDefault(exchange.getRequestHeaders().getFirst("Authorization"),
                new String[]{"omesh", "kumar"});
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("userId", UUID.randomUUID().toString());
        user.put("firstName", name[0]);
        user.put("lastName", name[1]);
        user.put("email", "omesh@example.com");
        respond(exchange, 200, Map.of("success", true, "message", "Profile fetched", "data", user));
    }

    private void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = json.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
